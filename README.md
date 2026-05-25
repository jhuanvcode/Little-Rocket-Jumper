# 🚀 Little-Rocket-Jumper - A Batalha do Foguetinho Contra o Mega Pombo

Um **jogo de plataforma e ação 2D** desenvolvido em **libGDX** onde você controla **Foguetinho**, um gari acrobata. Corra por uma cidade infinita, colete lixo, derrote inimigos e enfrente o **Mega Pombo** em uma batalha épica de 3 fases!

## 📋 Visão Geral

**Little-Rocket-Jumper** combina mecânicas de endless runner com combate dinâmico onde:
- **Fase de Corrida**: Corra automaticamente pela cidade, desvie de lixeiras, colete power-ups e derrote pombos voadores e ratos gigantes
- **Combate Corpo a Corpo**: Use socos, tiros e sacos de lixo arremessáveis contra inimigos
- **Boss Fight**: Após 120 segundos, o **Mega Pombo** aparece com 3 fases de dificuldade crescente
- **Sistema de Combo**: Acerte o boss em sequência para multiplicar sua pontuação

## 🎮 Como Funciona

### Controles do Jogo

| Tecla / Ação | Função |
|--------------|--------|
| **A / ←** | Andar para esquerda |
| **D / →** | Andar para direita |
| **SPACE** | Pular / Puloduplo |
| **S / ↓** | Cair mais rápido |
| **Botão Esquerdo (clique)** | Soco (ataque corpo a corpo) |
| **Botão Direito** | Disparar projétil |
| **Tecla E** | Arremessar saco de lixo |
| **Toque na tela** | Soco (versão mobile) |

### Fase 1: Corrida (120 segundos)
- O cenário rola automaticamente da direita para a esquerda
- Colete **sacos de lixo** (até 5) para usar como munição especial
- Power-ups disponíveis:
  - 🛡️ **Escudo** (Tampa de Lixo): Invulnerabilidade por 5 segundos
  - 💪 **Gigante**: Dobra o tamanho e dano por 5 segundos
- Inimigos:
  - 🐦 **Pombo Voador**: 3 HP, se move no ar
  - 🐀 **Rato do Chão**: 3 HP, persegue o jogador
- Obstáculos: **Lixeiras** causam dano ao toque

### Fase 2: Boss Fight - Mega Pombo (3 Fases)

| Fase | HP Restante | Características |
|------|-------------|-----------------|
| **Fase 1** | 100% → 60% | Dispara 1 projétil por vez, movimento oscilatório |
| **Fase 2** | 60% → 30% | Dispara 2 projéteis, mais rápido, cor alaranjada |
| **Fase 3** | 30% → 0% | Dispara 3 projéteis, **ataque de mergulho rasante**, cor vermelho/fogo |

**Mecânicas Especiais do Boss:**
- A cada 15% de HP perdido, o boss spawna uma horda de **pombinhos mini**
- Sistema de **combo**: acertos consecutivos aumentam a pontuação
- Barra de HP com marcadores visuais das fases
- Anúncio na tela: *"FASE 2 — POMBO FURIOSO!"* / *"FASE 3 — PODER MAXIMO!"*

## 🛠️ Tecnologias Utilizadas

- **Game Engine**: libGDX
- **Plataforma Desktop**: LWJGL3
- **Linguagem**: Java 17+
- **Build**: Gradle (com wrapper)
- **Gerenciamento de Assets**: Texturas em PNG, animações por sprite sheet

## 📦 Instalação e Execução

### 1. Clone o repositório
```bash
git clone https://github.com/jhuanvcode/Little-Rocket-Jumper.git
cd Little-Rocket-Jumper
./gradlew lwjgl3:run
gradlew.bat lwjgl3:run
./gradlew lwjgl3:jar

Little-Rocket-Jumper/
├── core/                    # Lógica principal do jogo
│   └── src/io/github/some_example_name/
│       ├── GameScreen.java  # Tela principal (~5000 linhas)
│       └── ...              # Classes auxiliares
├── lwjgl3/                  # Launcher desktop
│   └── src/.../Lwjgl3Launcher.java
├── assets/                  # Imagens, sprites e texturas
│   ├── FoguetinhoCorrendo.png
│   ├── FoguetinhoPulando.png
│   ├── foguetinhoAtackforte.png
│   ├── pombo.png
│   ├── Ratinhoprime.png
│   ├── BolaDoPombo.png
│   ├── DisparoFoguetinho.png
│   ├── sacoLixo.png
│   ├── lixeira.png
│   ├── up.png
│   ├── TampaDeLixoIcone.png
│   ├── fundoAzul.png
│   ├── nuvemCinza.png
│   ├── bairro.png
│   ├── predioFundo.png
│   ├── rua.png
│   ├── barraProgresso.png
│   ├── barraAzul.png
│   ├── gameOver.png
│   ├── voceVenceu.png
│   ├── botaoRetry.png
│   ├── botaoBack.png
│   ├── botaoSpace.png
│   ├── botoesAD.png
│   └── numero0-9.png        # Sprites de dígitos
├── gradle/                  # Wrapper e configurações
├── build.gradle             # Dependências libGDX
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle
└── README.md
🎯 Sistema de Pontuação
Ação	Pontos
Coletar lixo	+5
Matar pombo/rato	+1
Matar pombinho mini	+3
Acertar boss (tiro)	+2 × (combo/5)
Acertar boss (soco)	+5 × (combo/3)
Acertar boss (saco)	+10 × (combo/3)
Derrotar boss	+500

🚀 Comandos Gradle Úteis
./gradlew lwjgl3:run          # Executar o jogo
./gradlew lwjgl3:jar          # Gerar JAR executável
./gradlew clean               # Limpar arquivos de build
./gradlew --continue          # Continuar mesmo com erros
./gradlew --offline           # Usar cache offline
./gradlew --refresh-dependencies  # Forçar atualização de dependências
./gradlew build               # Build completo do projeto

✨ Funcionalidades Implementadas
🎮 Duas Fases de Jogo: Corrida (120s) + Boss Fight (3 fases)

👊 Sistema de Combate: Soco, tiro e arremesso de sacos de lixo

💪 Power-ups: Escudo (invulnerabilidade) e Gigante (dano dobrado)

🐦 Inimigos Variados: Pombo voador, rato terrestre, pombinhos mini

👑 Boss Inteligente: 3 fases com padrões de ataque diferentes

🔥 Sistema de Combo: Multiplicador de pontuação por acertos consecutivos

📊 HUD Completo: Pontuação, sacos coletados, barra de progresso

🎨 Animações Fluídas: Correr, pular, soco com sprite sheets

📱 Suporte Mobile: Toque na tela para soco

🌊 Screen Shake: Tremor de câmera em colisões e eventos

🎲 Spawn Dinâmico: Itens e obstáculos sem sobreposição

🔄 Pooling de Objetos: Otimização de performance para tiros e projéteis

🧪 Exemplo de Uso
Execute ./gradlew lwjgl3:run para iniciar o jogo

Use A/D ou ←/→ para se mover

Pule com SPACE sobre obstáculos e inimigos

Colete sacos de lixo (ícone amarelo) para munição especial

Aperte E para arremessar sacos contra inimigos

Use botão direito para atirar projéteis

Clique com botão esquerdo para dar soco corpo a corpo

Sobreviva por 120 segundos para enfrentar o Mega Pombo

Derrote as 3 fases do boss para vencer o jogo

📄 Licença
Projeto educacional desenvolvido com libGDX. Assets gráficos são de autoria própria ou livres para uso não comercial.
