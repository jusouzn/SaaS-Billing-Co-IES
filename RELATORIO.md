# Relatório técnico — diagnóstico de débito técnico

## 1. Matriz de code smells e violações arquiteturais

A análise abaixo considera o código legado originalmente entregue no commit `6dd9c8e`, no arquivo `SubscriptionBillingService-pratica.java`. As linhas indicadas referem-se a essa versão histórica, não à classe refatorada atual.

| # | Linha/trecho afetado | Code smell ou princípio violado | Juros do débito técnico — impacto na manutenção e na CI |
|---|---|---|---|
| 1 | 18–20 | **Long Parameter List** e **Primitive Obsession**: o método recebe oito parâmetros separados, incluindo vários `String`, `double` e `boolean`. | A chamada depende da ordem e do significado implícito de cada argumento; isso facilita trocas acidentais e torna a assinatura difícil de evoluir. Os testes precisam montar chamadas extensas e pouco legíveis, aumentando o custo de cada alteração. |
| 2 | 18–105 | **Long Method / God Method** e violação do **Single Responsibility Principle (SRP)**: o método calcula preços, consulta clientes, decide o pagamento, grava faturas, prepara e-mail e acumula logs. | Mudanças em uma regra de preço podem afetar o fluxo de persistência ou cobrança. A concentração de responsabilidades dificulta testes unitários isolados e amplia o risco de regressões, tornando a validação da CI mais lenta e menos precisa. |
| 3 | 29–57; 77; 85; 94 | **Primitive Obsession** para valores monetários: preços, descontos, juros e valores de fatura são representados por `double`, sem uma abstração monetária ou política explícita de arredondamento. | `double` representa números em ponto flutuante binário e pode produzir diferenças de precisão em cálculos financeiros. Isso pode gerar valores inconsistentes entre cálculo, persistência e recibo; a ausência de um tipo próprio também espalha as regras de arredondamento pelo código. |
| 4 | 14–16 | **Configuração e credenciais hardcoded**: URL, usuário e senha do banco estão declarados como constantes no serviço. | Alterar ambiente ou credenciais exige editar e recompilar o código, e valores sensíveis ficam expostos no repositório. Isso aumenta o risco operacional e torna a configuração de ambientes de teste/CI mais frágil. |
| 5 | 3–6; 59–64; 82–87 | **Dependência direta de I/O** e violação do **Dependency Inversion Principle (DIP)**: o serviço usa JDBC e `DriverManager` diretamente para consultar clientes e gravar faturas. | O domínio fica acoplado ao PostgreSQL e à infraestrutura local. Testes unitários dependem de banco/configuração externa ou de mecanismos difíceis de substituir, contrariando a exigência de executar a suíte da CI sem infraestrutura externa. |
| 6 | 25; 42 | **Dependência de relógio estático**: `LocalDate.now()` consulta a data real do sistema, usada tanto no cálculo de tempo de assinatura quanto na regra sazonal do cupom. | O resultado dos testes varia conforme a data em que são executados; a regra de novembro, por exemplo, não pode ser testada de forma determinística sem controlar o relógio. Isso torna testes frágeis e dificulta reproduzir falhas. |
| 7 | 74–80 | **Regra de negócio acoplada à implementação**: o próprio serviço considera o pagamento bem-sucedido apenas quando o token começa com `"tok_"`. | A regra não representa uma resposta de um provedor de pagamentos e fica misturada à orquestração. A troca ou simulação do gateway exige alterar o serviço, e testes não conseguem substituir o comportamento de pagamento por um colaborador controlável. |
| 8 | 89–98 | **Efeito colateral de notificação acoplado** e **código morto/inconsistente**: o serviço instancia `SimpleEmail`, mas a chamada `send()` está comentada (96); ainda assim, registra “E-mail disparado” (98). | Não há envio efetivo nesse trecho, embora o log indique sucesso. Isso mascara o comportamento real para operação e testes; além disso, preparar e-mail dentro do serviço de cobrança dificulta isolar a notificação e substituir SMTP na CI. |
| 9 | 100–102 | **Tratamento genérico de exceções** (`catch (Exception e)`) com falha convertida apenas em mensagem de log. | Erros distintos de banco, parsing ou outras operações são tratados da mesma forma e não são propagados ao chamador. A execução pode parecer concluída apesar de uma falha de infraestrutura, dificultando alertas, diagnóstico e assertions confiáveis nos testes. |
| 10 | 61–64; 83–87 | **Vazamento de recursos**: `PreparedStatement` e `ResultSet` são criados sem `try-with-resources` nem fechamento explícito. | Em execuções repetidas, recursos JDBC podem permanecer abertos até serem liberados indiretamente, consumindo conexões e cursores. Isso pode provocar falhas intermitentes sob carga e tornar testes e integrações menos estáveis. |
| 11 | 32–34; 38; 42–45; 50–51 | **Números e valores mágicos**: percentuais, valor fixo do cupom, quantidade de dias/meses e identificadores de cupons estão embutidos nas condições e operações. | Alterações nas políticas promocionais exigem localizar e editar valores espalhados no método; fica mais fácil atualizar uma ocorrência e esquecer outra. A intenção das regras e a cobertura de testes também ficam menos claras. |

### Observação de escopo

Esta seção registra os problemas identificados no código legado e seus impactos. As métricas comparativas e a revisão final da implementação estão nas seções seguintes.

### Decisões de comportamento na etapa 3

- A regra legada do pagamento foi mantida no adaptador `TokenPrefixPaymentGateway`: somente tokens não nulos iniciados por `tok_` são aprovados. Essa implementação preserva o comportamento simulado do código anterior; não substitui uma integração com provedor de pagamentos.
- O envio SMTP está ativo porque o requisito funcional descreve o envio de recibos. No código legado, a chamada `send()` estava comentada, apesar do log afirmar que o e-mail havia sido disparado. Se o SMTP falhar, a falha é propagada como `BillingInfrastructureException` após a tentativa de cobrança e a gravação da fatura; não há retentativa automática nesta etapa.
- `Customer` considera status nulo como inativo, preservando o comportamento anterior da comparação `"ACTIVE".equalsIgnoreCase(status)`.
- A data/hora da fatura é fornecida pelo `Clock` injetado como um `Instant`, em vez de usar `NOW()` no banco. Isso permite testes determinísticos e persiste um instante absoluto, independente de formatação de fuso.
- A composição das dependências fica em `BillingModule`; `DataSource`, host/porta SMTP e, opcionalmente, `Clock` são fornecidos pelo ambiente chamador, sem credenciais de banco embutidas.
- A remoção do `catch (Exception)` ocorreu na etapa 3 ao separar os adaptadores; falhas JDBC e SMTP são capturadas por tipo específico e propagadas. O passo 4 concluiu a extração de `PricingCalculator`, reduziu a fachada e adicionou testes de precificação.

## 2. Métricas antes e depois da refatoração

Para comparação, “antes” refere-se ao método `processMonthlyBilling` do arquivo legado e “depois” ao método principal `processMonthlyBilling(BillingCommand)` em [`SubscriptionBillingService.java`](./src/main/java/com/saas/billing/SubscriptionBillingService.java). LOC conta linhas não vazias do método, incluindo assinatura e chaves; comentários em linha com código permanecem contados. A complexidade ciclomática usa McCabe: 1 mais cada `if`/`else if`, `catch` e operador lógico `&&`/`||`.

| Métrica | Antes (legado) | Depois (passo 4) | Resultado / observação |
|---|---:|---:|---|
| LOC do método principal | 70 | 14 | Redução de 56 linhas de código não vazias; o bloco atual tem 15 linhas físicas, uma em branco. |
| Complexidade ciclomática do método principal | 17 | 3 | Redução de 14 pontos; os dois pontos de decisão restantes são cliente inexistente e cliente inativo. |
| Parâmetros do método principal | 8 | 1 | A entrada atual é encapsulada em `BillingCommand`. A sobrecarga legada `@Deprecated` continua com 8 parâmetros apenas para compatibilidade. |
| Dependências concretas de I/O no orquestrador | 2 | 0 | Antes: acesso JDBC direto e construção de `SimpleEmail`. Depois: I/O é delegado às portas; o serviço recebe quatro portas (`CustomerRepository`, `InvoiceRepository`, `PaymentGateway`, `NotificationService`). |
| Testes unitários executáveis sem banco ou SMTP | 0 | 28 | A versão legada não tinha suíte automatizada; agora todos os 28 testes passam usando testes unitários e doubles em memória, sem conectar a banco ou SMTP. |

As dependências concretas de I/O contam integrações/famílias, não cada classe JDBC individual. A `PricingCalculator` é criada diretamente pelo serviço, mas é uma classe pura de domínio, sem I/O; por isso não entra nessa métrica.

## 3. Revisão final

- `PricingCalculator` concentra as regras de fidelidade, desconto para ONGs, cupons, multa, juros e piso em zero; recebe `today` como argumento e não consulta relógio nem infraestrutura.
- O método principal de `processMonthlyBilling` tem 14 LOC segundo o critério acima e atua como fachada; a cobrança e a notificação ficam em `chargeAndNotify`.
- O cálculo de meses preserva a regra legada baseada na diferença de ano/mês do calendário, ignorando o dia. Os testes cobrem fronteiras de fidelidade, BLACKFRIDAY, WELCOME10, atraso, piso em zero e combinação de regras.
- O serviço principal usa `Clock` injetado; os testes do fluxo usam `Clock.fixed(...)` e colaboradores em memória. A suíte não exige banco ou SMTP.
- Não há `catch (Exception e)` genérico no fluxo. As falhas JDBC e SMTP são tratadas por tipos específicos nos adaptadores e propagadas como `BillingInfrastructureException`.
- A compatibilidade com a chamada antiga é mantida por uma sobrecarga `@Deprecated`; `planType` continua sendo aceito nela, mas não influencia o cálculo, como no legado.
- Validação final executada com `mvn test`: **28 testes, 0 falhas, 0 erros**.
