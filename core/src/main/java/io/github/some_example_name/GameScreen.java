package io.github.some_example_name;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class GameScreen implements Screen {

    // =========================================================================
    // CONSTANTES E CONFIGURAÇÕES GLOBAIS
    // =========================================================================

    static final float VIRTUAL_W            = 1920f;
    static final float VIRTUAL_H            = 1080f;
    static final float GRAVITY              = -1600f;
    static final float FAST_FALL_GRAVITY    = -6000f;
    static final float TEMPO_BOSS           = 120f;
    static final float PROGRESSO_MAX        = 1000f;
    static final float BOSS_SPEED_MULTIPLIER = 1.4f; // velocidade travada ao iniciar boss fight

    // Limiares de fase do boss (HP relativo)
    static final float FASE2_THRESHOLD = 0.60f;
    static final float FASE3_THRESHOLD = 0.30f;

    // Limiares de spawn de pombinhos pelo boss (% de HP perdido)
    static final float[] POMBO_SPAWN_THRESHOLDS = { 0.85f, 0.70f, 0.55f, 0.40f, 0.25f, 0.15f };

    // =========================================================================
    // ENUM DE ESTADO DO JOGO
    // =========================================================================

    enum GameState { READY, RUNNING, BOSS, GAME_OVER, VICTORY }
    private GameState state = GameState.READY;

    // =========================================================================
    // CORE / RENDERING
    // =========================================================================

    OrthographicCamera camera;
    Viewport viewport;
    SpriteBatch batch;
    Vector3 touchPoint = new Vector3();

    // Screen Shake
    float shakeTime      = 0;
    float shakeIntensity = 0;

    // =========================================================================
    // TEXTURAS
    // =========================================================================

    Texture backgroundBlue, cloudBackground, neighborhoodBackground, buildingBackground;
    Texture ground, trash, sacoLixo, texturaEscudo, texturaGigante;
    Texture playerTex, runSheet, jumpSheet, punchSheet;
    Texture pigeonSheet, groundEnemySheet;
    Texture barraProgresso, barraAzul, gameOver, voceVenceu, botaoRetry, botaoBack, botaoSpace, botoesAD;
    Texture bolaDoPombo, disparoFoguetinho;
    Texture pixelTex;
    Texture[] numeros = new Texture[10];
    TextureRegion barraAzulRegion;

    // =========================================================================
    // ANIMAÇÕES
    // =========================================================================

    Animation<TextureRegion> runAnimation, jumpAnimation, punchAnimation;
    Animation<TextureRegion> pigeonAnimation, groundEnemyAnimation;

    // =========================================================================
    // CLASSES INTERNAS
    // =========================================================================

    // ── Player ────────────────────────────────────────────────────────────────
    class Player {
        float x = 100, y = 100, velocityY = 0;
        boolean isOnGround = true, canDoubleJump = false, isPunching = false;
        float jumpStateTime = 0, punchStateTime = 0;
        float invencibilidadeTimer = 0, giganteTimer = 0, escudoTimer = 0;
        float shootCooldown = 0;
        int   sacosDeLixo = 0;       // quantidade de sacos coletados (max 5)
        float sacoThrowCooldown = 0f;
        boolean temEscudo = false;
        Rectangle hitbox      = new Rectangle();
        Rectangle punchHitbox = new Rectangle();

        void updateHitboxes() {
            float scale = (giganteTimer > 0) ? 2f : 1f;
            hitbox.set(x + (65 * scale), y + (10 * scale), 90 * scale, 130 * scale);
            if (isPunching)
                punchHitbox.set(x + (70 * scale), y + (20 * scale), 180 * scale, 120 * scale);
            else
                punchHitbox.set(0, 0, 0, 0);
        }
    }

    // ── Inimigo (pombo voador / rato do chão) ─────────────────────────────────
    class Inimigo {
        float x, y, baseY, velocityX, knockbackTimer;
        float flashRedTimer = 0;
        int hp;
        boolean alreadyHit;
        Rectangle hitbox = new Rectangle();
        float width, height, ox, oy, timeOffset;

        Inimigo(float w, float h, float ox, float oy, int hp) {
            this.width = w; this.height = h;
            this.ox = ox; this.oy = oy;
            this.hp = hp;
        }

        void updateHitbox() { hitbox.set(x + ox, y + oy, width, height); }
    }

    // ── Pombinho Mini (lançado pelo boss) ─────────────────────────────────────
    class PombinhoMini {
        float x, y, velocityX, velocityY;
        float timeOffset    = 0;
        float hp            = 1;
        float flashRedTimer = 0;
        boolean alreadyHit  = false;
        Rectangle hitbox    = new Rectangle();

        PombinhoMini(float x, float y, float vx, float vy) {
            this.x = x; this.y = y;
            this.velocityX = vx; this.velocityY = vy;
            this.timeOffset = MathUtils.random(0f, 2f);
        }

        void update(float delta) {
            x += velocityX * delta;
            y += velocityY * delta;
            velocityY -= 400f * delta; // gravidade leve
            if (y < 100f) { y = 100f; velocityY = Math.abs(velocityY) * 0.4f; }
            if (flashRedTimer > 0) flashRedTimer -= delta;
            timeOffset += delta;
            hitbox.set(x + 20, y + 10, 90, 80);
        }
    }

    // ── Saco de Lixo Arremessável (pool) ─────────────────────────────────────
    class SacoArremessado implements Pool.Poolable {
        float x, y, velocityX, velocityY;
        float rotation = 0f;
        boolean isActive = false;
        Rectangle hitbox = new Rectangle();

        void init(float x, float y) {
            this.x        = x;
            this.y        = y;
            this.velocityX = 1400f;
            this.velocityY = 300f;
            this.rotation  = 0f;
            this.isActive  = true;
        }

        void update(float delta) {
            x += velocityX * delta;
            y += velocityY * delta;
            velocityY -= 1200f * delta; // arco parabólico
            rotation  += 360f * delta;
            hitbox.set(x + 10, y + 10, 120, 120);
        }

        @Override
        public void reset() {
            x = 0; y = 0; velocityX = 0; velocityY = 0;
            rotation = 0; isActive = false;
            hitbox.set(0, 0, 0, 0);
        }
    }

    // ── Tiro (pool) ───────────────────────────────────────────────────────────
    class Tiro implements Pool.Poolable {
        float x, y, speed, speedY;
        boolean isPlayer;
        float dano = 1.5f;
        Rectangle hitbox = new Rectangle();

        void init(float x, float y, float speed, boolean isPlayer) {
            this.x = x; this.y = y;
            this.speed = speed; this.speedY = 0;
            this.isPlayer = isPlayer;
        }

        void initDiagonal(float x, float y, float speed, float speedY, boolean isPlayer) {
            this.x = x; this.y = y;
            this.speed = speed; this.speedY = speedY;
            this.isPlayer = isPlayer;
        }

        void update(float delta) {
            x += speed  * delta;
            y += speedY * delta;
            hitbox.set(x, y, isPlayer ? 60 : 80, isPlayer ? 10 : 30);
        }

        @Override
        public void reset() {
            x = 0; y = 0; speed = 0; speedY = 0;
            dano = 1.5f;
            hitbox.set(0, 0, 0, 0);
        }
    }

    // ── Obstáculo (lixeira) ───────────────────────────────────────────────────
    class Obstaculo {
        float x;
        Rectangle hitbox = new Rectangle();

        Obstaculo(float x) { this.x = x; }

        void updateHitbox() { hitbox.set(x + 45, 100, 70, 80); }
    }

    // ── Item coletável genérico (lixo / escudo / gigante) ────────────────────
    class Item {
        float x, y;
        Rectangle hitbox = new Rectangle();

        Item(float x, float y) { this.x = x; this.y = y; }
    }

    // ── Item Saco de Lixo Coletável ───────────────────────────────────────────
    class ItemSaco {
        float x, y;
        float bobOffset;
        Rectangle hitbox = new Rectangle();

        ItemSaco(float x, float y) {
            this.x = x; this.y = y;
            this.bobOffset = MathUtils.random(0f, MathUtils.PI2);
        }
    }

    // =========================================================================
    // BOSS — sistema de 3 fases (SEM DASH)
    // =========================================================================

    class Boss {
        float x = VIRTUAL_W + 300;
        float y = VIRTUAL_H / 2f;
        float maxHp = 100, hp = 100;
        float stateTime  = 0;
        float shootTimer = 0;
        float flashRedTimer = 0;
        Rectangle hitbox = new Rectangle();

        // Fases
        int  faseAtual       = 1;
        boolean anunciadoFase2 = false;
        boolean anunciadoFase3 = false;

        // Spawn de pombinhos por limiar de HP
        boolean[] pomboThresholdDisparado = new boolean[POMBO_SPAWN_THRESHOLDS.length];

        // Mergulho Rasante (Fase 3)
        float mergulhoCooldownTimer = 0f;
        float mergulhoCooldown      = 5f;
        boolean estaEmMergulho      = false;
        float mergulhoTimer         = 0f;
        float mergulhoDuracao       = 0.6f;
        float mergulhoOrigemY       = 0f;
        static final float MERGULHO_GROUND_Y = 200f;

        // Combo do jogador
        int   comboContador = 0;
        float comboTimer    = 0f;
        float comboDuracao  = 2.5f;

        // Anúncio de fase na tela
        float  anuncioTimer = 0f;
        String anuncioTexto = "";

        // ── Update principal ──────────────────────────────────────────────────
        void update(float delta) {
            stateTime += delta;
            if (flashRedTimer > 0) flashRedTimer -= delta;

            atualizarFase();
            verificarSpawnPombinhos();

            if (anuncioTimer > 0) anuncioTimer -= delta;

            // Entrada do boss na tela
            if (x > VIRTUAL_W - 500) {
                x -= 200 * delta;
                hitbox.set(x + 50, y + 50, 400, 400);
                return;
            }

            // Movimento oscilatório (quando não mergulha)
            if (!estaEmMergulho) {
                float amplitude  = 250f + (faseAtual - 1) * 60f;
                float frequencia = 3f  + (faseAtual - 1) * 0.8f;
                y = 300 + MathUtils.sin(stateTime * frequencia) * amplitude;
            }

            atualizarTiro(delta);
            if (faseAtual >= 3) atualizarMergulho(delta);

            hitbox.set(x + 50, y + 50, 400, 400);
        }

        // ── Spawn de pombinhos por limiar de HP ──────────────────────────────
        private void verificarSpawnPombinhos() {
            float ratio = hp / maxHp;
            for (int i = 0; i < POMBO_SPAWN_THRESHOLDS.length; i++) {
                if (!pomboThresholdDisparado[i] && ratio <= POMBO_SPAWN_THRESHOLDS[i]) {
                    pomboThresholdDisparado[i] = true;
                    int qtd = 2 + i; // 2, 3, 4, 5, 6, 7
                    spawnarPombinhos(qtd);
                }
            }
        }

        private void spawnarPombinhos(int qtd) {
            for (int i = 0; i < qtd; i++) {
                float angle = MathUtils.random(150f, 210f) * MathUtils.degreesToRadians;
                float speed = MathUtils.random(300f, 700f);
                float vx    = MathUtils.cos(angle) * speed;
                float vy    = MathUtils.sin(angle) * speed + MathUtils.random(100f, 400f);
                pombinhosMini.add(new PombinhoMini(x + 200, y + 200, vx, vy));
            }
            addScreenShake(0.25f, 20f);
        }

        // ── Sistema de tiro ───────────────────────────────────────────────────
        private void atualizarTiro(float delta) {
            float intervalo = faseAtual == 1 ? 2.0f
                : faseAtual == 2 ? 1.5f
                  :                  1.2f;
            shootTimer += delta;
            if (shootTimer < intervalo) return;
            shootTimer = 0;

            float origemX = x + 60f;
            float origemY = y + 400f;
            float alvoX   = player.hitbox.x + player.hitbox.width  / 2f;
            float alvoY   = player.hitbox.y + player.hitbox.height / 2f;

            float dx   = alvoX - origemX;
            float dy   = alvoY - origemY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist == 0) dist = 1;
            float nx = dx / dist;
            float ny = dy / dist;

            float velocidade = 1000f + (faseAtual - 1) * 150f;

            float[] spreads;
            if (faseAtual == 1)      spreads = new float[]{ 0f };
            else if (faseAtual == 2) spreads = new float[]{ -0.26f, 0.26f };
            else                     spreads = new float[]{ -0.44f, 0f, 0.44f };

            for (float ang : spreads) {
                float cos = MathUtils.cos(ang);
                float sin = MathUtils.sin(ang);
                float vx  = (nx * cos - ny * sin) * velocidade;
                float vy  = (nx * sin + ny * cos) * velocidade;
                Tiro t = tiroPool.obtain();
                t.initDiagonal(origemX, origemY, vx, vy, false);
                tiros.add(t);
            }
            addScreenShake(0.1f, 10f);
        }

        // ── Mergulho Rasante (apenas Fase 3) ─────────────────────────────────
        private void atualizarMergulho(float delta) {
            if (estaEmMergulho) {
                mergulhoTimer += delta;
                float progresso = mergulhoTimer / mergulhoDuracao;
                if (progresso >= 1f) {
                    estaEmMergulho       = false;
                    mergulhoCooldownTimer = 0;
                    addScreenShake(0.5f, 40f);
                    // Ondas ao bater no chão
                    for (int i = 0; i < 4; i++) {
                        Tiro onda = tiroPool.obtain();
                        float sinal = (i % 2 == 0) ? 1f : -1f;
                        float speed = (600f + i * 200f) * sinal;
                        onda.init(x + 200, MERGULHO_GROUND_Y + 40, speed, false);
                        tiros.add(onda);
                    }
                } else {
                    float t = progresso * progresso;
                    y = mergulhoOrigemY + (MERGULHO_GROUND_Y - mergulhoOrigemY) * t;
                }
            } else {
                mergulhoCooldownTimer += delta;
                if (mergulhoCooldownTimer >= mergulhoCooldown) {
                    mergulhoOrigemY  = y;
                    estaEmMergulho   = true;
                    mergulhoTimer    = 0f;
                    addScreenShake(0.15f, 12f);
                }
            }
        }

        // ── Transição de fases ────────────────────────────────────────────────
        private void atualizarFase() {
            float ratio = hp / maxHp;
            if (ratio <= FASE3_THRESHOLD && faseAtual < 3) {
                faseAtual = 3;
                if (!anunciadoFase3) {
                    anunciadoFase3 = true;
                    anuncioTexto   = "FASE 3 — PODER MAXIMO!";
                    anuncioTimer   = 2.5f;
                    addScreenShake(0.6f, 50f);
                    comboContador  = 0;
                }
            } else if (ratio <= FASE2_THRESHOLD && faseAtual < 2) {
                faseAtual = 2;
                if (!anunciadoFase2) {
                    anunciadoFase2 = true;
                    anuncioTexto   = "FASE 2 — POMBO FURIOSO!";
                    anuncioTimer   = 2.5f;
                    addScreenShake(0.4f, 35f);
                }
            }
        }

        // ── Registrar hit e combo ─────────────────────────────────────────────
        void registrarHit(float dano) {
            hp           -= dano;
            flashRedTimer = 0.1f;
            comboContador++;
            comboTimer = comboDuracao;
        }

        void atualizarCombo(float delta) {
            if (comboTimer > 0) {
                comboTimer -= delta;
                if (comboTimer <= 0) comboContador = 0;
            }
        }
    }

    // =========================================================================
    // INSTÂNCIAS DO JOGO
    // =========================================================================

    Player  player      = new Player();
    Inimigo pigeon      = new Inimigo(106, 92,  66, 66, 3);
    Inimigo groundEnemy = new Inimigo(90,  130, 65, 10, 3);
    Boss    megaPombo   = new Boss();

    Array<Item>         lixos          = new Array<>();
    Array<Item>         escudos        = new Array<>();
    Array<Item>         gigantes       = new Array<>();
    Array<ItemSaco>     itensSaco      = new Array<>();
    Array<PombinhoMini> pombinhosMini  = new Array<>();
    Array<Obstaculo>    lixeiras       = new Array<>();

    private final Pool<Tiro> tiroPool = new Pool<Tiro>() {
        @Override protected Tiro newObject() { return new Tiro(); }
    };
    private final Pool<SacoArremessado> sacoPool = new Pool<SacoArremessado>() {
        @Override protected SacoArremessado newObject() { return new SacoArremessado(); }
    };

    Array<Tiro>            tiros             = new Array<>();
    Array<SacoArremessado> sacosArremessados = new Array<>();

    // =========================================================================
    // VARIÁVEIS DE ESTADO
    // =========================================================================

    float stateTime = 0f, tempoFase = 0f, progresso = 0f;
    float currentSpeedMultiplier = 1f;
    float groundX, cloudX, neighborhoodX, buildingX;

    // Timers de spawn
    float lixoSpawnTimer        = 0f;
    float escudoSpawnTimer      = 0f;
    float giganteSpawnTimer     = 0f;
    float trashSpawnTimer       = 0f;
    float sacoSpawnTimer        = 0f;

    // Intervalos de spawn
    float currentTrashInterval   = 3f;
    float currentEscudoInterval  = 10f;
    float currentGiganteInterval = 20f;
    float currentSacoInterval    = 8f;

    int pontuacao = 0;

    // Animação de Game Over / Vitória
    float gameOverY            = 2000;
    float gameOverVelocity     = 0;
    float botoesY              = 2400;
    boolean gameOverAnimFinished = false;

    // Tutorial
    float   tutorialAlpha  = 1f;
    boolean tutorialFading = false;

    // =========================================================================
    // INICIALIZAÇÃO
    // =========================================================================

    @Override
    public void show() {
        batch    = new SpriteBatch();
        camera   = new OrthographicCamera();
        viewport = new FitViewport(VIRTUAL_W, VIRTUAL_H, camera);
        camera.setToOrtho(false, VIRTUAL_W, VIRTUAL_H);

        gerarTexturasDinamicas();
        carregarTexturas();
        configurarAnimacoes();

        lixeiras.add(new Obstaculo(VIRTUAL_W));
        resetInimigo(pigeon, true);
        resetInimigo(groundEnemy, false);
    }

    private void gerarTexturasDinamicas() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixelTex = new Texture(pixmap);
        pixmap.dispose();
    }

    private void carregarTexturas() {
        backgroundBlue         = new Texture("fundoAzul.png");
        cloudBackground        = new Texture("nuvemCinza.png");
        neighborhoodBackground = new Texture("bairro.png");
        buildingBackground     = new Texture("predioFundo.png");
        ground                 = new Texture("rua.png");
        trash                  = new Texture("lixeira.png");
        sacoLixo               = new Texture("sacoLixo.png");
        texturaEscudo          = new Texture("TampaDeLixoIcone.png");
        texturaGigante         = new Texture("up.png");

        playerTex  = new Texture("FoguetinhoParado.png");
        runSheet   = new Texture("FoguetinhoCorrendo.png");
        jumpSheet  = new Texture("FoguetinhoPulando.png");
        punchSheet = new Texture("foguetinhoAtackforte.png");

        pigeonSheet      = new Texture("pombo.png");
        groundEnemySheet = new Texture("Ratinhoprime.png");

        barraProgresso = new Texture("barraProgresso.png");
        barraAzul      = new Texture("barraAzul.png");
        barraAzulRegion = new TextureRegion(barraAzul);
        gameOver       = new Texture("gameOver.png");
        voceVenceu     = new Texture("voceVenceu.png");
        botaoRetry     = new Texture("botaoRetry.png");
        botaoBack      = new Texture("botaoBack.png");
        botaoSpace     = new Texture("botaoSpace.png");
        botoesAD       = new Texture("botoesAD.png");
        bolaDoPombo    = new Texture("BolaDoPombo.png");
        disparoFoguetinho = new Texture("DisparoFoguetinho.png");

        for (int i = 0; i <= 9; i++) {
            try { numeros[i] = new Texture("numero" + i + ".png"); }
            catch (Exception e) { /* ignorar */ }
        }
    }

    private void configurarAnimacoes() {
        runAnimation         = buildAnim(runSheet,         0.10f);
        jumpAnimation        = buildAnim(jumpSheet,        0.08f);
        punchAnimation       = buildAnim(punchSheet,       0.05f);
        pigeonAnimation      = buildAnim(pigeonSheet,      0.08f);
        groundEnemyAnimation = buildAnim(groundEnemySheet, 0.10f);
    }

    private Animation<TextureRegion> buildAnim(Texture sheet, float frameDuration) {
        TextureRegion[][] tmp    = TextureRegion.split(sheet, 256, 256);
        TextureRegion[]   frames = new TextureRegion[9];
        int index = 0;
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                frames[index++] = tmp[i][j];
        return new Animation<>(frameDuration, frames);
    }

    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================

    /** Retorna uma posição X livre de sobreposição com obstáculos e itens. */
    private float getLivreX(float xDesejado, float margem) {
        float xLivre = xDesejado;
        boolean sobreposto;
        int maxTentativas = 0;
        do {
            sobreposto = false;
            for (Obstaculo obs : lixeiras) if (Math.abs(xLivre - obs.x) < margem) { xLivre = obs.x + margem; sobreposto = true; }
            for (Item item  : lixos)       if (Math.abs(xLivre - item.x) < margem) { xLivre = item.x + margem; sobreposto = true; }
            for (Item item  : escudos)     if (Math.abs(xLivre - item.x) < margem) { xLivre = item.x + margem; sobreposto = true; }
            for (Item item  : gigantes)    if (Math.abs(xLivre - item.x) < margem) { xLivre = item.x + margem; sobreposto = true; }
            maxTentativas++;
        } while (sobreposto && maxTentativas < 15);
        return xLivre;
    }

    public void addScreenShake(float duration, float intensity) {
        shakeTime      = duration;
        shakeIntensity = intensity;
    }

    // =========================================================================
    // LOOP PRINCIPAL
    // =========================================================================

    @Override
    public void render(float rawDelta) {
        float delta = Math.min(rawDelta, 1 / 30f);

        // Screen shake
        if (shakeTime > 0) {
            shakeTime -= delta;
            camera.position.x = (VIRTUAL_W / 2f) + MathUtils.random(-shakeIntensity, shakeIntensity);
            camera.position.y = (VIRTUAL_H / 2f) + MathUtils.random(-shakeIntensity, shakeIntensity);
        } else {
            camera.position.set(VIRTUAL_W / 2f, VIRTUAL_H / 2f, 0);
        }
        camera.update();

        if (state == GameState.GAME_OVER || state == GameState.VICTORY) {
            updateGameOver(delta);
            drawScene();
            return;
        }

        if (state == GameState.RUNNING || state == GameState.BOSS) {
            updateLogic(delta);
        }

        updatePlayer(delta);
        updateTiros(delta);
        updateSacosArremessados(delta);
        updateCollisions(delta);
        updateTutorialFade(delta);

        drawScene();
    }

    // =========================================================================
    // UPDATE — LÓGICA DO JOGO
    // =========================================================================

    private void updateLogic(float delta) {
        stateTime += delta * currentSpeedMultiplier;

        if (state == GameState.RUNNING) {
            tempoFase += delta;
            progresso  = Math.min((tempoFase / TEMPO_BOSS) * PROGRESSO_MAX, PROGRESSO_MAX);
            currentSpeedMultiplier = 1f + (tempoFase / TEMPO_BOSS) * 1.5f;

            if (tempoFase >= TEMPO_BOSS) iniciarBossFight();

        } else if (state == GameState.BOSS) {
            megaPombo.update(delta);
            megaPombo.atualizarCombo(delta);
            updatePombinhosMini(delta);
        }

        updateSpawners(delta);
        updateScrollAndEntities(delta);
        updateEnemies(delta);
    }

    // ── Update de tiros ───────────────────────────────────────────────────────
    private void updateTiros(float delta) {
        for (int i = tiros.size - 1; i >= 0; i--) {
            Tiro t = tiros.get(i);
            t.update(delta);
            if (t.x > VIRTUAL_W + 800 || t.x < -800 || t.y > VIRTUAL_H + 200 || t.y < -200) {
                tiroPool.free(t);
                tiros.removeIndex(i);
            }
        }
    }

    // ── Update de sacos arremessados ──────────────────────────────────────────
    private void updateSacosArremessados(float delta) {
        for (int i = sacosArremessados.size - 1; i >= 0; i--) {
            SacoArremessado s = sacosArremessados.get(i);
            s.update(delta);
            if (s.x > VIRTUAL_W + 400 || s.y < 50f) {
                sacoPool.free(s);
                sacosArremessados.removeIndex(i);
            }
        }
    }

    // ── Update de pombinhos mini ──────────────────────────────────────────────
    private void updatePombinhosMini(float delta) {
        for (int i = pombinhosMini.size - 1; i >= 0; i--) {
            PombinhoMini p = pombinhosMini.get(i);
            p.update(delta);
            if (p.x < -300 || p.x > VIRTUAL_W + 300) {
                pombinhosMini.removeIndex(i);
            }
        }
    }

    // ── Spawners de itens e obstáculos ────────────────────────────────────────
    private void updateSpawners(float delta) {
        // Saco coletável — disponível em qualquer fase
        sacoSpawnTimer += delta * currentSpeedMultiplier;
        if (sacoSpawnTimer >= currentSacoInterval) {
            float spawnY = 200f + MathUtils.random(0f, 200f);
            itensSaco.add(new ItemSaco(VIRTUAL_W + 150f, spawnY));
            sacoSpawnTimer     = 0;
            currentSacoInterval = MathUtils.random(6f, 14f);
        }

        if (state == GameState.BOSS) {
            // No boss: apenas powerups spawnam
            escudoSpawnTimer += delta;
            if (escudoSpawnTimer >= currentEscudoInterval) {
                escudos.add(new Item(VIRTUAL_W + 150f, 200f + MathUtils.random(0f, 200f)));
                escudoSpawnTimer     = 0;
                currentEscudoInterval = MathUtils.random(4f, 10f);
            }
            giganteSpawnTimer += delta;
            if (giganteSpawnTimer >= currentGiganteInterval) {
                gigantes.add(new Item(VIRTUAL_W + 150f, 200f + MathUtils.random(0f, 200f)));
                giganteSpawnTimer     = 0;
                currentGiganteInterval = MathUtils.random(6f, 12f);
            }
            return; // sem obstáculos nem inimigos no boss
        }

        // Fase de corrida: lixo, escudo, gigante, lixeiras
        lixoSpawnTimer += delta * currentSpeedMultiplier;
        if (lixoSpawnTimer >= 4f && lixos.size < 10) {
            lixos.add(new Item(getLivreX(VIRTUAL_W + 150f, 250f), 120f));
            lixoSpawnTimer = 0;
        }

        escudoSpawnTimer += delta * currentSpeedMultiplier;
        if (escudoSpawnTimer >= currentEscudoInterval) {
            escudos.add(new Item(getLivreX(VIRTUAL_W + 150f, 250f), 280f + MathUtils.random(0, 120f)));
            escudoSpawnTimer     = 0;
            currentEscudoInterval = MathUtils.random(10f, 25f);
        }

        giganteSpawnTimer += delta * currentSpeedMultiplier;
        if (giganteSpawnTimer >= currentGiganteInterval) {
            gigantes.add(new Item(getLivreX(VIRTUAL_W + 150f, 250f), 280f + MathUtils.random(0, 120f)));
            giganteSpawnTimer     = 0;
            currentGiganteInterval = MathUtils.random(15f, 35f);
        }

        trashSpawnTimer += delta * currentSpeedMultiplier;
        if (trashSpawnTimer >= currentTrashInterval) {
            int   qtd    = 1 + MathUtils.random(0, 2);
            float startX = getLivreX(VIRTUAL_W + 150f, 250f);
            for (int i = 0; i < qtd; i++) lixeiras.add(new Obstaculo(startX + (i * 160f)));
            trashSpawnTimer     = 0;
            currentTrashInterval = MathUtils.random(2f, 4.5f);
        }
    }

    // ── Scroll do cenário e entidades ─────────────────────────────────────────
    private void updateScrollAndEntities(float delta) {
        float effectiveMultiplier = (state == GameState.BOSS) ? BOSS_SPEED_MULTIPLIER : currentSpeedMultiplier;
        float speed = 300 * effectiveMultiplier * delta;

        groundX       = (groundX       - speed)       % -2600;
        neighborhoodX = (neighborhoodX - speed)       % -2000;
        cloudX        = (cloudX        - 50 * delta)  % -2600;
        buildingX     = (buildingX     - 20 * delta)  % -2600;

        if (state == GameState.BOSS) {
            // Powerups e sacos scrollam junto ao cenário no boss
            scrollItems(escudos,   speed, true);
            scrollItems(gigantes,  speed, true);
            scrollSacos(speed);
            return;
        }

        // Fase de corrida
        for (int i = lixeiras.size - 1; i >= 0; i--) {
            Obstaculo obs = lixeiras.get(i);
            obs.x -= speed; obs.updateHitbox();
            if (obs.x < -300) lixeiras.removeIndex(i);
        }
        scrollItems(lixos,    speed, false);
        scrollItems(escudos,  speed, true);
        scrollItems(gigantes, speed, true);
        scrollSacos(speed);
    }

    private void scrollItems(Array<Item> items, float speed, boolean bobbing) {
        for (int i = items.size - 1; i >= 0; i--) {
            Item item = items.get(i);
            item.x -= speed;
            float hitY = bobbing ? item.y + MathUtils.sin(tempoFase * 4f) * 20f : item.y;
            item.hitbox.set(item.x, hitY, 100, 100);
            if (item.x < -300) items.removeIndex(i);
        }
    }

    private void scrollSacos(float speed) {
        for (int i = itensSaco.size - 1; i >= 0; i--) {
            ItemSaco s = itensSaco.get(i);
            s.x -= speed;
            s.hitbox.set(s.x, s.y + MathUtils.sin(tempoFase * 3f + s.bobOffset) * 15f, 100, 100);
            if (s.x < -300) itensSaco.removeIndex(i);
        }
    }

    // ── Update de inimigos (apenas na fase de corrida) ────────────────────────
    private void updateEnemies(float delta) {
        if (state == GameState.BOSS) return;
        float baseSpeed = 500 * currentSpeedMultiplier * delta;

        // Pombo voador
        if (pigeon.flashRedTimer > 0) pigeon.flashRedTimer -= delta;
        if (pigeon.knockbackTimer > 0) {
            pigeon.knockbackTimer -= delta;
            pigeon.x += pigeon.velocityX * delta;
            aplicarAtritoInimigo(pigeon, delta);
        } else {
            pigeon.velocityX = 0;
            pigeon.x -= baseSpeed;
            float targetY = player.y + 20f;
            float skyY    = 500f;
            float midY    = (targetY + skyY) / 2f;
            float ampY    = (skyY - targetY) / 2f;
            pigeon.y = midY + MathUtils.sin(tempoFase * 3.5f) * ampY;
        }
        pigeon.timeOffset += delta * currentSpeedMultiplier;
        pigeon.updateHitbox();
        if (pigeon.x < -300 || pigeon.hp <= 0) resetInimigo(pigeon, true);

        // Rato do chão
        if (groundEnemy.flashRedTimer > 0) groundEnemy.flashRedTimer -= delta;
        if (groundEnemy.knockbackTimer > 0) {
            groundEnemy.knockbackTimer -= delta;
            groundEnemy.x += groundEnemy.velocityX * delta;
            aplicarAtritoInimigo(groundEnemy, delta);
        } else {
            groundEnemy.velocityX = 0;
            float dist       = groundEnemy.x - player.x;
            float actualSpeed = (dist > 0 && dist < 700) ? 850 : 400;
            groundEnemy.x -= actualSpeed * currentSpeedMultiplier * delta;
        }
        groundEnemy.timeOffset += delta * currentSpeedMultiplier;
        groundEnemy.updateHitbox();
        if (groundEnemy.x < -300 || groundEnemy.hp <= 0) resetInimigo(groundEnemy, false);
    }

    private void aplicarAtritoInimigo(Inimigo inimigo, float delta) {
        float friction = 8f * Math.abs(inimigo.velocityX) * delta;
        inimigo.velocityX = inimigo.velocityX > 0
            ? Math.max(0, inimigo.velocityX - friction)
            : Math.min(0, inimigo.velocityX + friction);
    }

    // =========================================================================
    // UPDATE — PLAYER
    // =========================================================================

    private void updatePlayer(float delta) {
        // Cooldowns
        if (player.shootCooldown    > 0) player.shootCooldown    -= delta;
        if (player.sacoThrowCooldown > 0) player.sacoThrowCooldown -= delta;

        // Pulo
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (player.isOnGround) {
                player.velocityY = 850; player.isOnGround = false;
                player.canDoubleJump = true; player.jumpStateTime = 0;
                iniciarJogoSeReady();
            } else if (player.canDoubleJump) {
                player.velocityY = 700; player.canDoubleJump = false; player.jumpStateTime = 0;
            }
        }

        // Gravidade
        if (!player.isOnGround) {
            player.jumpStateTime += delta;
            boolean fastFall = Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN);
            player.velocityY += (fastFall ? FAST_FALL_GRAVITY : GRAVITY) * delta;
        }

        player.y += player.velocityY * delta;
        if (player.y <= 100) {
            player.y = 100; player.velocityY = 0;
            player.isOnGround = true; player.canDoubleJump = false;
        }

        // Movimento horizontal
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            player.x -= 400 * delta; iniciarJogoSeReady();
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            player.x += 400 * delta; iniciarJogoSeReady();
        }
        player.x = MathUtils.clamp(player.x, 0, 1400);

        // Tiro normal (botão direito do mouse)
        if ((Gdx.input.isButtonPressed(Input.Buttons.RIGHT) || Gdx.input.isTouched(1))
            && player.shootCooldown <= 0) {
            player.shootCooldown = 0.15f;
            float scale    = (player.giganteTimer > 0) ? 2f : 1f;
            float danoTiro = (player.giganteTimer > 0) ? 3f : 1.5f;
            Tiro t = tiroPool.obtain();
            t.init(player.x + (150 * scale), player.y + (80 * scale), 1800f, true);
            t.dano = danoTiro;
            tiros.add(t);
            iniciarJogoSeReady();
        }

        // Arremesso de saco (tecla E)
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)
            && player.sacosDeLixo > 0 && player.sacoThrowCooldown <= 0) {
            float scale = (player.giganteTimer > 0) ? 2f : 1f;
            SacoArremessado s = sacoPool.obtain();
            s.init(player.x + 140 * scale, player.y + 80 * scale);
            sacosArremessados.add(s);
            player.sacosDeLixo--;
            player.sacoThrowCooldown = 0.4f;
            iniciarJogoSeReady();
        }

        // Soco (toque / clique esquerdo)
        if (player.isPunching) {
            player.punchStateTime += delta;
            if (punchAnimation.isAnimationFinished(player.punchStateTime)) player.isPunching = false;
        }
        if (Gdx.input.justTouched() && !player.isPunching && !Gdx.input.isTouched(1)) {
            player.isPunching = true; player.punchStateTime = 0;
            pigeon.alreadyHit = false; groundEnemy.alreadyHit = false;
            for (PombinhoMini p : pombinhosMini) p.alreadyHit = false;
        }

        player.updateHitboxes();
    }

    // =========================================================================
    // UPDATE — COLISÕES
    // =========================================================================

    private void updateCollisions(float delta) {
        // Timers de status
        if (player.invencibilidadeTimer > 0) player.invencibilidadeTimer -= delta;
        if (player.giganteTimer         > 0) player.giganteTimer         -= delta;
        if (player.escudoTimer          > 0) {
            player.escudoTimer -= delta;
            if (player.escudoTimer <= 0) player.temEscudo = false;
        }

        // Coleta de powerups
        for (int i = escudos.size - 1; i >= 0; i--) {
            if (player.hitbox.overlaps(escudos.get(i).hitbox)) {
                player.temEscudo = true; player.escudoTimer = 5f; escudos.removeIndex(i);
            }
        }
        for (int i = gigantes.size - 1; i >= 0; i--) {
            if (player.hitbox.overlaps(gigantes.get(i).hitbox)) {
                player.giganteTimer = 5f; gigantes.removeIndex(i); addScreenShake(0.2f, 15f);
            }
        }
        for (int i = lixos.size - 1; i >= 0; i--) {
            if (player.hitbox.overlaps(lixos.get(i).hitbox)) {
                pontuacao += 5; lixos.removeIndex(i);
            }
        }

        // Coleta de sacos
        for (int i = itensSaco.size - 1; i >= 0; i--) {
            if (player.hitbox.overlaps(itensSaco.get(i).hitbox)) {
                player.sacosDeLixo = Math.min(player.sacosDeLixo + 1, 5);
                itensSaco.removeIndex(i);
                addScreenShake(0.05f, 5f);
            }
        }

        // Dano por contato
        boolean sofreuDano = false;

        for (Obstaculo obs : lixeiras)
            if (player.hitbox.overlaps(obs.hitbox)) sofreuDano = true;

        if (state != GameState.BOSS) {
            if (player.hitbox.overlaps(pigeon.hitbox))      { sofreuDano = true; aplicarKnockbackInimigo(pigeon, 250f); }
            if (player.hitbox.overlaps(groundEnemy.hitbox)) { sofreuDano = true; aplicarKnockbackInimigo(groundEnemy, 250f); }
        } else {
            sofreuDano |= colisaoBoss();
            for (PombinhoMini p : pombinhosMini)
                if (player.hitbox.overlaps(p.hitbox)) { sofreuDano = true; break; }
        }

        if (sofreuDano) tomarDano();

        // Colisão de tiros
        for (int i = tiros.size - 1; i >= 0; i--) {
            Tiro t = tiros.get(i);
            if (processarTiro(t)) { tiroPool.free(t); tiros.removeIndex(i); }
        }

        // Colisão de sacos arremessados
        for (int i = sacosArremessados.size - 1; i >= 0; i--) {
            SacoArremessado s = sacosArremessados.get(i);
            if (processarSaco(s)) { sacoPool.free(s); sacosArremessados.removeIndex(i); }
        }

        // Soco do player
        if (player.isPunching) processarSoco();
    }

    /** Retorna true se o player sofreu dano por contato direto com o boss. */
    private boolean colisaoBoss() {
        Rectangle bh = megaPombo.hitbox;
        if (!player.hitbox.overlaps(bh)) return false;

        float playerCenterX = player.hitbox.x + player.hitbox.width  / 2f;
        float playerCenterY = player.hitbox.y + player.hitbox.height / 2f;
        float bossCenterX   = bh.x + bh.width  / 2f;
        float bossCenterY   = bh.y + bh.height / 2f;

        float overlapLeft   = (player.hitbox.x + player.hitbox.width) - bh.x;
        float overlapRight  = (bh.x + bh.width) - player.hitbox.x;
        float overlapTop    = (player.hitbox.y + player.hitbox.height) - bh.y;

        boolean vinhoDebaixo = playerCenterY < bossCenterY
            && overlapTop < overlapLeft && overlapTop < overlapRight;

        if (vinhoDebaixo) {
            player.y = bh.y - player.hitbox.height - (player.hitbox.y - player.y) - 2f;
            if (player.velocityY > 0) player.velocityY = 0;
            return false;
        }

        // Colisão lateral — empurra e causa dano
        if (playerCenterX < bossCenterX) player.x -= overlapLeft  + 2f;
        else                             player.x += overlapRight + 2f;
        return true;
    }

    /** Processa colisão de um tiro. Retorna true se o tiro deve ser destruído. */
    private boolean processarTiro(Tiro t) {
        if (t.isPlayer) {
            if (state != GameState.BOSS) {
                if (t.hitbox.overlaps(pigeon.hitbox)) {
                    pigeon.hp--; pigeon.flashRedTimer = 0.1f; pontuacao += 1; return true;
                }
                if (t.hitbox.overlaps(groundEnemy.hitbox)) {
                    groundEnemy.hp--; groundEnemy.flashRedTimer = 0.1f; pontuacao += 1; return true;
                }
            } else {
                if (t.hitbox.overlaps(megaPombo.hitbox)) {
                    float dano = t.dano + (megaPombo.faseAtual - 1) * 0.25f;
                    megaPombo.registrarHit(dano);
                    pontuacao += 2 * Math.max(1, megaPombo.comboContador / 5);
                    if (megaPombo.hp <= 0) vitoriaBoss();
                    return true;
                }
                // Tiro acerta pombinho mini
                for (int j = pombinhosMini.size - 1; j >= 0; j--) {
                    if (t.hitbox.overlaps(pombinhosMini.get(j).hitbox)) {
                        pombinhosMini.removeIndex(j);
                        pontuacao += 3;
                        addScreenShake(0.05f, 8f);
                        return true;
                    }
                }
            }
        } else {
            if (t.hitbox.overlaps(player.hitbox)) { tomarDano(); return true; }
        }
        return false;
    }

    /** Processa colisão de um saco arremessado. Retorna true se deve ser destruído. */
    private boolean processarSaco(SacoArremessado s) {
        if (state == GameState.BOSS) {
            if (s.hitbox.overlaps(megaPombo.hitbox)) {
                float dano = 8f + (megaPombo.faseAtual - 1) * 1.5f;
                megaPombo.registrarHit(dano);
                addScreenShake(0.35f, 30f);
                pontuacao += 10 * Math.max(1, megaPombo.comboContador / 3);
                if (megaPombo.hp <= 0) vitoriaBoss();
                return true;
            }
            for (int j = pombinhosMini.size - 1; j >= 0; j--) {
                if (s.hitbox.overlaps(pombinhosMini.get(j).hitbox)) {
                    pombinhosMini.removeIndex(j);
                    pontuacao += 5;
                    addScreenShake(0.1f, 12f);
                    return true;
                }
            }
        } else {
            if (s.hitbox.overlaps(pigeon.hitbox)) {
                pigeon.hp -= 2; pigeon.flashRedTimer = 0.2f;
                pontuacao += 5; aplicarKnockbackInimigo(pigeon, 600f); return true;
            }
            if (s.hitbox.overlaps(groundEnemy.hitbox)) {
                groundEnemy.hp -= 2; groundEnemy.flashRedTimer = 0.2f;
                pontuacao += 5; aplicarKnockbackInimigo(groundEnemy, 600f); return true;
            }
        }
        return false;
    }

    /** Processa o soco do player contra inimigos e pombinhos. */
    private void processarSoco() {
        if (state != GameState.BOSS) {
            if (!pigeon.alreadyHit && player.punchHitbox.overlaps(pigeon.hitbox)) {
                pigeon.hp -= 2; pigeon.alreadyHit = true; pigeon.flashRedTimer = 0.2f;
                aplicarKnockbackInimigo(pigeon, 800f); addScreenShake(0.15f, 20f);
            }
            if (!groundEnemy.alreadyHit && player.punchHitbox.overlaps(groundEnemy.hitbox)) {
                groundEnemy.hp -= 2; groundEnemy.alreadyHit = true; groundEnemy.flashRedTimer = 0.2f;
                aplicarKnockbackInimigo(groundEnemy, 800f); addScreenShake(0.15f, 20f);
            }
        } else {
            if (player.punchHitbox.overlaps(megaPombo.hitbox) && player.invencibilidadeTimer <= 0) {
                float dano = 5f + (megaPombo.faseAtual - 1) * 1f;
                megaPombo.registrarHit(dano);
                player.invencibilidadeTimer = 0.5f;
                addScreenShake(0.2f, 25f);
                pontuacao += 5 * Math.max(1, megaPombo.comboContador / 3);
                if (megaPombo.hp <= 0) vitoriaBoss();
            }
            for (int j = pombinhosMini.size - 1; j >= 0; j--) {
                PombinhoMini p = pombinhosMini.get(j);
                if (!p.alreadyHit && player.punchHitbox.overlaps(p.hitbox)) {
                    p.alreadyHit = true;
                    pombinhosMini.removeIndex(j);
                    pontuacao += 3;
                    addScreenShake(0.08f, 10f);
                }
            }
        }
    }

    private void aplicarKnockbackInimigo(Inimigo inimigo, float forca) {
        float dir = (inimigo.x >= player.x) ? 1f : -1f;
        inimigo.velocityX    = dir * forca;
        inimigo.knockbackTimer = 0.35f;
    }

    // =========================================================================
    // DANO / MORTE / VITÓRIA
    // =========================================================================

    private void tomarDano() {
        if (player.invencibilidadeTimer > 0 || player.giganteTimer > 0) return;
        addScreenShake(0.3f, 30f);
        if (player.temEscudo) {
            player.temEscudo   = false;
            player.escudoTimer = 0;
            player.invencibilidadeTimer = 1.5f;
        } else {
            triggerDeath();
        }
    }

    private void triggerDeath() {
        state            = GameState.GAME_OVER;
        gameOverY        = 2000;
        gameOverVelocity = -1800;
        gameOverAnimFinished = false;
    }

    private void vitoriaBoss() {
        state = GameState.VICTORY;
        addScreenShake(1.0f, 50f);
        pontuacao += 500;
        gameOverY        = 2000;
        gameOverVelocity = -1800;
        gameOverAnimFinished = false;
    }

    // =========================================================================
    // INICIAR / REINICIAR
    // =========================================================================

    private void iniciarJogoSeReady() {
        if (state == GameState.READY) { state = GameState.RUNNING; tutorialFading = true; }
    }

    private void iniciarBossFight() {
        state = GameState.BOSS;
        currentSpeedMultiplier = BOSS_SPEED_MULTIPLIER;

        lixos.clear(); lixeiras.clear();
        tiroPool.freeAll(tiros); tiros.clear();
        sacoPool.freeAll(sacosArremessados); sacosArremessados.clear();
        pombinhosMini.clear();

        // Reset do boss
        megaPombo.x                 = VIRTUAL_W + 500;
        megaPombo.y                 = VIRTUAL_H / 2f;
        megaPombo.hp                = megaPombo.maxHp;
        megaPombo.faseAtual         = 1;
        megaPombo.anunciadoFase2    = false;
        megaPombo.anunciadoFase3    = false;
        megaPombo.anuncioTimer      = 0f;
        megaPombo.anuncioTexto      = "";
        megaPombo.estaEmMergulho    = false;
        megaPombo.mergulhoCooldownTimer = 0f;
        megaPombo.comboContador     = 0;
        megaPombo.stateTime         = 0f;
        megaPombo.shootTimer        = 0f;
        megaPombo.pomboThresholdDisparado = new boolean[POMBO_SPAWN_THRESHOLDS.length];

        sacoSpawnTimer       = 0f;
        currentSacoInterval  = 8f;
        escudoSpawnTimer     = 0f;
        giganteSpawnTimer    = 0f;
    }

    private void reiniciarJogo() {
        state                  = GameState.READY;
        currentSpeedMultiplier = 1f;
        player                 = new Player();
        stateTime = 0; lixoSpawnTimer = 0; escudoSpawnTimer = 0; giganteSpawnTimer = 0;
        sacoSpawnTimer = 0;
        pontuacao = 0; progresso = 0; tempoFase = 0;
        groundX = 0; cloudX = 0; neighborhoodX = 0; buildingX = 0;

        lixeiras.clear(); lixos.clear(); escudos.clear(); gigantes.clear();
        itensSaco.clear(); pombinhosMini.clear();
        tiroPool.freeAll(tiros); tiros.clear();
        sacoPool.freeAll(sacosArremessados); sacosArremessados.clear();

        lixeiras.add(new Obstaculo(VIRTUAL_W));
        trashSpawnTimer      = 0f;
        currentTrashInterval  = 3f;
        currentEscudoInterval  = MathUtils.random(10f, 25f);
        currentGiganteInterval = MathUtils.random(15f, 35f);
        currentSacoInterval    = MathUtils.random(6f, 14f);

        resetInimigo(pigeon, true);
        resetInimigo(groundEnemy, false);

        tutorialAlpha  = 1f;
        tutorialFading = false;
        camera.position.set(VIRTUAL_W / 2f, VIRTUAL_H / 2f, 0);
    }

    private void resetInimigo(Inimigo inimigo, boolean isVoador) {
        inimigo.x             = 2200 + MathUtils.random(0, 1500f);
        inimigo.y             = isVoador ? 180f : 100f;
        inimigo.baseY         = inimigo.y;
        inimigo.hp            = 3;
        inimigo.velocityX     = 0;
        inimigo.knockbackTimer = 0;
    }

    // =========================================================================
    // TUTORIAL FADE
    // =========================================================================

    private void updateTutorialFade(float delta) {
        if (tutorialFading) tutorialAlpha = Math.max(0f, tutorialAlpha - delta / 2f);
    }

    // =========================================================================
    // GAME OVER / VITÓRIA — UPDATE
    // =========================================================================

    private void updateGameOver(float delta) {
        gameOverVelocity -= 3400f * delta;
        gameOverY  += gameOverVelocity * delta;
        botoesY    += gameOverVelocity * delta;

        if (gameOverY <= 207f) {
            gameOverY = 207f; botoesY = 90f;
            if (!gameOverAnimFinished) {
                gameOverVelocity     *= -0.28f;
                gameOverAnimFinished  = true;
                addScreenShake(0.2f, 15f);
            } else {
                gameOverVelocity = (Math.abs(gameOverVelocity) > 40f) ? gameOverVelocity * -0.18f : 0f;
            }
        }

        if (Gdx.input.justTouched()) {
            touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            camera.unproject(touchPoint, viewport.getScreenX(), viewport.getScreenY(),
                viewport.getScreenWidth(), viewport.getScreenHeight());
            if (touchPoint.x >= 560f && touchPoint.x <= 920f &&
                touchPoint.y >= botoesY && touchPoint.y <= botoesY + 220f) reiniciarJogo();
            if (touchPoint.x >= 980f && touchPoint.x <= 1320f &&
                touchPoint.y >= botoesY && touchPoint.y <= botoesY + 220f) Gdx.app.exit();
        }
    }

    // =========================================================================
    // RENDERIZAÇÃO
    // =========================================================================

    private void drawScene() {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        drawBackground();

        if (state == GameState.BOSS || state == GameState.VICTORY) {
            drawBossScene();
        } else {
            drawRunningScene();
        }

        drawTiros();
        drawSacosArremessados();
        drawPlayer();
        drawHUD();
        drawGameOverOrVictory();

        batch.end();
    }

    private void drawBackground() {
        batch.draw(backgroundBlue, 0, 0, VIRTUAL_W, VIRTUAL_H);
        batch.draw(buildingBackground, buildingX,          200, 2600, 850);
        batch.draw(buildingBackground, buildingX + 2600,   200, 2600, 850);
        batch.draw(cloudBackground,    cloudX,             250, 2600, 900);
        batch.draw(cloudBackground,    cloudX + 2600,      250, 2600, 900);
        batch.draw(neighborhoodBackground, neighborhoodX,        203, 2000, 466);
        batch.draw(neighborhoodBackground, neighborhoodX + 2000, 203, 2000, 466);
        batch.draw(ground, groundX,          -160, 2600, 1200);
        batch.draw(ground, groundX + 2600,   -160, 2600, 1200);
    }

    private void drawRunningScene() {
        for (Obstaculo obs : lixeiras) batch.draw(trash, obs.x, 100, 160, 160);
        for (Item item : lixos)        batch.draw(sacoLixo, item.x, item.y, 140, 140);
        for (Item item : escudos)      batch.draw(texturaEscudo, item.x, item.hitbox.y, 100, 100);
        for (Item item : gigantes)     batch.draw(texturaGigante, item.x, item.hitbox.y, 100, 100);

        // Sacos coletáveis (tom amarelado para distinguir)
        for (ItemSaco s : itensSaco) {
            batch.setColor(0.9f, 0.7f, 0.2f, 1f);
            batch.draw(sacoLixo, s.x, s.hitbox.y, 110, 110);
            batch.setColor(Color.WHITE);
        }

        // Pombo voador
        if (pigeon.flashRedTimer > 0) batch.setColor(Color.RED);
        batch.draw(pigeonAnimation.getKeyFrame(pigeon.timeOffset, true), pigeon.x, pigeon.y, 240, 240);
        batch.setColor(Color.WHITE);

        // Rato do chão
        if (groundEnemy.flashRedTimer > 0) batch.setColor(Color.RED);
        batch.draw(groundEnemyAnimation.getKeyFrame(groundEnemy.timeOffset, true), groundEnemy.x, groundEnemy.y, 180, 180);
        batch.setColor(Color.WHITE);
    }

    private void drawBossScene() {
        // Powerups e sacos
        for (Item item : escudos)  batch.draw(texturaEscudo, item.x, item.hitbox.y, 100, 100);
        for (Item item : gigantes) batch.draw(texturaGigante, item.x, item.hitbox.y, 100, 100);
        for (ItemSaco s : itensSaco) {
            batch.setColor(0.9f, 0.7f, 0.2f, 1f);
            batch.draw(sacoLixo, s.x, s.hitbox.y, 110, 110);
            batch.setColor(Color.WHITE);
        }

        // Pombinhos mini
        for (PombinhoMini p : pombinhosMini) {
            if (p.flashRedTimer > 0) batch.setColor(Color.RED);
            else                     batch.setColor(0.8f, 0.8f, 1f, 1f);
            batch.draw(pigeonAnimation.getKeyFrame(p.timeOffset, true), p.x, p.y, 140, 140);
            batch.setColor(Color.WHITE);
        }

        // Boss — cor por fase
        Color corFase = megaPombo.faseAtual == 3 ? new Color(1f, 0.4f, 0f, 1f)
            : megaPombo.faseAtual == 2 ? new Color(1f, 0.7f, 0.2f, 1f)
              :                            Color.WHITE;
        batch.setColor(megaPombo.flashRedTimer > 0 ? Color.RED : corFase);
        batch.draw(pigeonAnimation.getKeyFrame(megaPombo.stateTime, true),
            megaPombo.x, megaPombo.y, 500, 500);
        batch.setColor(Color.WHITE);

        drawBossHPBar();

        if (megaPombo.comboContador > 1)
            drawDynamicScore(megaPombo.comboContador, VIRTUAL_W / 2f - 100, 900);

        // Indicador de pombinhos restantes
        if (!pombinhosMini.isEmpty()) {
            batch.setColor(0.8f, 0.8f, 1f, 0.8f);
            batch.draw(pixelTex, 50, 780, 30, 30);
            batch.setColor(Color.WHITE);
            drawDynamicScore(pombinhosMini.size, 90, 780);
        }
    }

    private void drawBossHPBar() {
        float barW = 400f, barH = 30f;
        float barX = megaPombo.x + 50;
        float barY = megaPombo.y + 520;

        batch.setColor(Color.DARK_GRAY);
        batch.draw(pixelTex, barX, barY, barW, barH);

        float ratioHp = megaPombo.hp / megaPombo.maxHp;
        Color corBarra = megaPombo.faseAtual == 3 ? Color.ORANGE
            : megaPombo.faseAtual == 2 ? Color.YELLOW : Color.RED;
        batch.setColor(corBarra);
        batch.draw(pixelTex, barX, barY, barW * ratioHp, barH);

        // Marcadores de fase
        batch.setColor(Color.WHITE);
        batch.draw(pixelTex, barX + barW * FASE2_THRESHOLD - 2, barY, 4, barH);
        batch.draw(pixelTex, barX + barW * FASE3_THRESHOLD - 2, barY, 4, barH);
        batch.setColor(Color.WHITE);
    }

    private void drawTiros() {
        for (Tiro t : tiros) {
            if (t.isPlayer) {
                batch.setColor(Color.WHITE);
                batch.draw(disparoFoguetinho, t.x, t.y, 96, 32);
            } else {
                float largura = 80 + (state == GameState.BOSS ? (megaPombo.faseAtual - 1) * 20 : 0);
                batch.setColor(Color.WHITE);
                batch.draw(bolaDoPombo, t.x, t.y - (largura / 2 - 15), largura, largura);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawSacosArremessados() {
        for (SacoArremessado s : sacosArremessados) {
            batch.draw(sacoLixo,
                s.x + 60, s.y + 60,
                -60, -60,
                120, 120,
                1f, 1f,
                s.rotation,
                0, 0,
                sacoLixo.getWidth(), sacoLixo.getHeight(),
                false, false);
        }
    }

    private void drawPlayer() {
        // Cor do player por estado
        if (player.temEscudo)
            batch.setColor(0.3f, 0.8f, 1f, 0.9f);
        else if (player.invencibilidadeTimer > 0 && ((int)(player.invencibilidadeTimer * 10) % 2 == 0))
            batch.setColor(1f, 1f, 1f, 0.4f);

        float scale = (player.giganteTimer > 0) ? 2f : 1f;
        if (player.isPunching)
            batch.draw(punchAnimation.getKeyFrame(player.punchStateTime, false),
                player.x, player.y - (18 * scale), 218 * scale, 218 * scale);
        else if (!player.isOnGround)
            batch.draw(jumpAnimation.getKeyFrame(player.jumpStateTime, false),
                player.x, player.y - (34 * scale), 228 * scale, 228 * scale);
        else if (state == GameState.RUNNING || state == GameState.BOSS)
            batch.draw(runAnimation.getKeyFrame(stateTime, true),
                player.x, player.y - (34 * scale), 228 * scale, 228 * scale);
        else
            batch.draw(playerTex, player.x, player.y, 180 * scale, 180 * scale);

        batch.setColor(Color.WHITE);
    }

    private void drawHUD() {
        // HUD de sacos de lixo
        if (state == GameState.BOSS || player.sacosDeLixo > 0) {
            batch.setColor(0.9f, 0.7f, 0.2f, 1f);
            batch.draw(sacoLixo, 40, 680, 80, 80);
            batch.setColor(Color.WHITE);
            batch.draw(pixelTex, 125, 700, 20, 5);
            batch.draw(pixelTex, 125, 700, 5, 20);
            drawDynamicScore(player.sacosDeLixo, 140, 695);
        }

        // Barra de progresso
        float ratio    = progresso / PROGRESSO_MAX;
        int   barraWidth = (int)(barraAzul.getWidth() * ratio);
        if (barraWidth > 0) {
            barraAzulRegion.setRegion(0, 0, barraWidth, barraAzul.getHeight());
            batch.draw(barraAzulRegion, 610f, 600f, 730f * ratio, 750f);
        }
        batch.draw(barraProgresso, 625f, 780f, 700f, 367f);

        // Pontuação
        batch.draw(sacoLixo, 40, 860, 200, 200);
        drawDynamicScore(pontuacao, 220, 920);

        // Tutorial
        if (tutorialAlpha > 0f) {
            batch.setColor(1f, 1f, 1f, tutorialAlpha);
            float spX = (VIRTUAL_W - 420f) / 2f + 60f;
            batch.draw(botaoSpace, spX,         590f, 420f, 210f);
            batch.draw(botoesAD,  spX - 250f,   590f, 300f, 210f);
            batch.setColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawGameOverOrVictory() {
        if (state == GameState.GAME_OVER) {
            batch.draw(gameOver, 460, gameOverY, 1000, 666);
            batch.draw(botaoRetry, 560f, botoesY, 360f, 220f);
            batch.draw(botaoBack,  980f, botoesY, 340f, 220f);
        } else if (state == GameState.VICTORY) {
            batch.draw(voceVenceu, 460, gameOverY, 1000, 666);
            batch.draw(botaoRetry, 560f, botoesY, 360f, 220f);
            batch.draw(botaoBack,  980f, botoesY, 340f, 220f);
        }
    }

    private void drawDynamicScore(int score, float startX, float startY) {
        String scoreStr = String.valueOf(score);
        for (int i = 0; i < scoreStr.length(); i++) {
            int digit = Character.getNumericValue(scoreStr.charAt(i));
            if (numeros[digit] != null)
                batch.draw(numeros[digit], startX + (i * 70), startY, 80, 80);
        }
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Override public void resize(int w, int h) { viewport.update(w, h, true); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        batch.dispose();
        backgroundBlue.dispose();
        cloudBackground.dispose();
        neighborhoodBackground.dispose();
        buildingBackground.dispose();
        ground.dispose();
        trash.dispose();
        sacoLixo.dispose();
        texturaEscudo.dispose();
        texturaGigante.dispose();
        pixelTex.dispose();
        playerTex.dispose();
        runSheet.dispose();
        jumpSheet.dispose();
        punchSheet.dispose();
        pigeonSheet.dispose();
        groundEnemySheet.dispose();
        barraProgresso.dispose();
        barraAzul.dispose();
        gameOver.dispose();
        voceVenceu.dispose();
        botaoRetry.dispose();
        botaoBack.dispose();
        botaoSpace.dispose();
        botoesAD.dispose();
        bolaDoPombo.dispose();
        disparoFoguetinho.dispose();
        for (Texture t : numeros) if (t != null) t.dispose();
    }
}
