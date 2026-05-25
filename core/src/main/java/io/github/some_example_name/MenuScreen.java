package io.github.some_example_name;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class MenuScreen implements Screen {

    Main game;

    SpriteBatch batch;

    // ── Câmera e viewport ─────────────────────────────────────────────────────
    // Resolução virtual fixa — o jogo sempre "pensa" que a tela tem esse tamanho.
    // Troque pelos valores que você já usa no GameScreen (ex.: 1920×1080).
    static final float VIRTUAL_W = 1920f;
    static final float VIRTUAL_H = 1080f;

    OrthographicCamera camera;
    Viewport viewport;

    // ── Imagens ───────────────────────────────────────────────────────────────
    Texture fundo;
    Texture botaoPlay;
    Texture botaoExit;

    // ── Botão Play ────────────────────────────────────────────────────────────
    float playX, playY;
    float playWidth  = 300;
    float playHeight = 120;

    // ── Botão Exit ────────────────────────────────────────────────────────────
    float exitX, exitY;
    float exitWidth  = 180;
    float exitHeight = 100;

    public MenuScreen(Main game) {
        this.game = game;
    }

    @Override
    public void show() {
        batch = new SpriteBatch();

        // Câmera fixa no espaço virtual
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIRTUAL_W, VIRTUAL_H, camera);
        camera.setToOrtho(false, VIRTUAL_W, VIRTUAL_H);

        fundo     = new Texture("cenarioInicio.png");
        botaoPlay = new Texture("botaoPlay.png");
        botaoExit = new Texture("botaoExit.png");

        // Posições em coordenadas virtuais (iguais a antes)
        playX = (VIRTUAL_W - playWidth) / 2f;
        playY = 450;

        exitX = 20;
        exitY = 20;
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Atualiza a câmera e aplica o viewport
        camera.update();
        batch.setProjectionMatrix(camera.combined);

        batch.begin();

        batch.draw(fundo,     0,     0,     VIRTUAL_W, VIRTUAL_H);
        batch.draw(botaoPlay, playX, playY, playWidth,  playHeight);
        batch.draw(botaoExit, exitX, exitY, exitWidth,  exitHeight);

        batch.end();

        // ── Input ─────────────────────────────────────────────────────────────
        if (Gdx.input.justTouched()) {
            // Converte coordenadas de tela → coordenadas virtuais
            float mouseX = Gdx.input.getX();
            float mouseY = Gdx.input.getY();

            com.badlogic.gdx.math.Vector3 touch =
                new com.badlogic.gdx.math.Vector3(mouseX, mouseY, 0);
            camera.unproject(touch, viewport.getScreenX(), viewport.getScreenY(),
                viewport.getScreenWidth(), viewport.getScreenHeight());

            if (touch.x >= playX && touch.x <= playX + playWidth
                && touch.y >= playY && touch.y <= playY + playHeight) {
                game.setScreen(new GameScreen());
            }

            if (touch.x >= exitX && touch.x <= exitX + exitWidth
                && touch.y >= exitY && touch.y <= exitY + exitHeight) {
                Gdx.app.exit();
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        // Isso é tudo que precisa — o viewport recalcula as letterboxes automaticamente
        viewport.update(width, height, true);
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        batch.dispose();
        fundo.dispose();
        botaoPlay.dispose();
        botaoExit.dispose();
    }
}
