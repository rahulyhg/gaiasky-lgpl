package gaia.cu9.ari.gaiaorbit.render;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.RenderableSorter;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.bitfire.postprocessing.filters.Glow;
import com.bitfire.utils.ShaderLoader;

import gaia.cu9.ari.gaiaorbit.GaiaSky;
import gaia.cu9.ari.gaiaorbit.event.EventManager;
import gaia.cu9.ari.gaiaorbit.event.Events;
import gaia.cu9.ari.gaiaorbit.event.IObserver;
import gaia.cu9.ari.gaiaorbit.render.IPostProcessor.PostProcessBean;
import gaia.cu9.ari.gaiaorbit.render.system.AbstractRenderSystem;
import gaia.cu9.ari.gaiaorbit.render.system.AbstractRenderSystem.RenderSystemRunnable;
import gaia.cu9.ari.gaiaorbit.render.system.FontRenderSystem;
import gaia.cu9.ari.gaiaorbit.render.system.GalaxyRenderSystem;
import gaia.cu9.ari.gaiaorbit.render.system.IRenderSystem;
import gaia.cu9.ari.gaiaorbit.render.system.LineQuadRenderSystem;
import gaia.cu9.ari.gaiaorbit.render.system.LineRenderSystem;
import gaia.cu9.ari.gaiaorbit.render.system.ModelBatchRenderSystem;
import gaia.cu9.ari.gaiaorbit.render.system.PixelRenderSystem;
import gaia.cu9.ari.gaiaorbit.render.system.QuadRenderSystem;
import gaia.cu9.ari.gaiaorbit.render.system.VolumeCloudsRenderSystem;
import gaia.cu9.ari.gaiaorbit.scenegraph.CameraManager.CameraMode;
import gaia.cu9.ari.gaiaorbit.scenegraph.ICamera;
import gaia.cu9.ari.gaiaorbit.scenegraph.Particle;
import gaia.cu9.ari.gaiaorbit.scenegraph.SceneGraphNode.RenderGroup;
import gaia.cu9.ari.gaiaorbit.util.Constants;
import gaia.cu9.ari.gaiaorbit.util.GSEnumSet;
import gaia.cu9.ari.gaiaorbit.util.GlobalConf;
import gaia.cu9.ari.gaiaorbit.util.GlobalResources;
import gaia.cu9.ari.gaiaorbit.util.Logger;
import gaia.cu9.ari.gaiaorbit.util.ds.Multilist;
import gaia.cu9.ari.gaiaorbit.util.math.MathUtilsd;
import gaia.cu9.ari.gaiaorbit.util.override.AtmosphereGroundShaderProvider;
import gaia.cu9.ari.gaiaorbit.util.override.AtmosphereShaderProvider;

/**
 * Renders a scenegraph.
 * 
 * @author Toni Sagrista
 *
 */
public class SceneGraphRenderer extends AbstractRenderer implements IProcessRenderer, IObserver {

    /** Contains the flags representing each type's visibility **/
    public static GSEnumSet<ComponentType> visible;
    /** Contains the last update time of each of the flags **/
    public static long[] times;
    /** Alpha values for each type **/
    public static float[] alphas;

    public AbstractRenderSystem[] pixelRenderSystems;

    private ShaderProgram starShader, fontShader;

    private int maxTexSize;

    FrameBuffer depthfb;

    /** Render lists for all render groups **/
    public static Map<RenderGroup, Multilist<IRenderable>> render_lists;

    // Two model batches, for front (models), back and atmospheres
    private SpriteBatch spriteBatch, fontBatch;

    private Array<IRenderSystem> renderProcesses;

    RenderSystemRunnable blendNoDepthRunnable, blendDepthRunnable;

    /** The particular current scene graph renderer **/
    private ISGR sgr;
    /**
     * Renderer vector, with 0 = normal, 1 = stereoscopic, 2 = FOV, 3 = cubemap
     **/
    private ISGR[] sgrs;

    final int SGR_DEFAULT_IDX = 0, SGR_STEREO_IDX = 1, SGR_FOV_IDX = 2, SGR_CUBEMAP_IDX = 3;

    public SceneGraphRenderer() {
        super();
    }

    @Override
    public void initialize(AssetManager manager) {
        IntBuffer intBuffer = BufferUtils.newIntBuffer(16);
        Gdx.gl20.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, intBuffer);
        maxTexSize = intBuffer.get();
        Logger.info(this.getClass().getSimpleName(), "Max texture size: " + maxTexSize + "^2 pixels");

        ShaderLoader.Pedantic = false;
        ShaderProgram.pedantic = false;
        starShader = new ShaderProgram(Gdx.files.internal("shader/star.vertex.glsl"), Gdx.files.internal("shader/star.fragment.glsl"));
        if (!starShader.isCompiled()) {
            Logger.error(new RuntimeException(), this.getClass().getName() + " - Star shader compilation failed:\n" + starShader.getLog());
        }

        fontShader = new ShaderProgram(Gdx.files.internal("shader/font.vertex.glsl"), Gdx.files.internal("shader/font.fragment.glsl"));
        if (!fontShader.isCompiled()) {
            Logger.error(new RuntimeException(), this.getClass().getName() + " - Font shader compilation failed:\n" + fontShader.getLog());
        }

        int numLists = GlobalConf.performance.MULTITHREADING ? GlobalConf.performance.NUMBER_THREADS() : 1;
        RenderGroup[] renderGroups = RenderGroup.values();
        render_lists = new HashMap<RenderGroup, Multilist<IRenderable>>(renderGroups.length);
        for (RenderGroup rg : renderGroups) {
            render_lists.put(rg, new Multilist<IRenderable>(numLists, 40000));
        }

        ShaderProvider sp = new AtmosphereGroundShaderProvider(Gdx.files.internal("shader/default.vertex.glsl"), Gdx.files.internal("shader/default.fragment.glsl"));
        ShaderProvider spnormal = Constants.webgl ? sp : new AtmosphereGroundShaderProvider(Gdx.files.internal("shader/normal.vertex.glsl"), Gdx.files.internal("shader/normal.fragment.glsl"));
        ShaderProvider spatm = new AtmosphereShaderProvider(Gdx.files.internal("shader/atm.vertex.glsl"), Gdx.files.internal("shader/atm.fragment.glsl"));
        ShaderProvider spsurface = new DefaultShaderProvider(Gdx.files.internal("shader/default.vertex.glsl"), Gdx.files.internal("shader/starsurface.fragment.glsl"));
        ShaderProvider spbeam = new DefaultShaderProvider(Gdx.files.internal("shader/default.vertex.glsl"), Gdx.files.internal("shader/beam.fragment.glsl"));

        RenderableSorter noSorter = new RenderableSorter() {
            @Override
            public void sort(Camera camera, Array<Renderable> renderables) {
                // Does nothing
            }
        };

        ModelBatch modelBatchB = new ModelBatch(sp, noSorter);
        ModelBatch modelBatchF = Constants.webgl ? modelBatchB : new ModelBatch(spnormal, noSorter);
        ModelBatch modelBatchAtm = new ModelBatch(spatm, noSorter);
        ModelBatch modelBatchS = new ModelBatch(spsurface, noSorter);
        ModelBatch modelBatchBeam = new ModelBatch(spbeam, noSorter);
        ModelBatch modelBatchCloseUp = new ModelBatch(spnormal, noSorter);

        // Sprites
        spriteBatch = GlobalResources.spriteBatch;
        spriteBatch.enableBlending();

        // Font batch
        fontBatch = new SpriteBatch(1000, fontShader);
        fontBatch.enableBlending();

        ComponentType[] comps = ComponentType.values();

        // Set reference
        visible = GSEnumSet.noneOf(ComponentType.class);
        for (int i = 0; i < GlobalConf.scene.VISIBILITY.length; i++) {
            if (GlobalConf.scene.VISIBILITY[i]) {
                visible.add(ComponentType.values()[i]);
            }

        }

        times = new long[comps.length];
        alphas = new float[comps.length];
        for (int i = 0; i < comps.length; i++) {
            times[i] = -20000l;
            alphas[i] = 1f;
        }

        /**
         * DEPTH BUFFER BITS
         */

        intBuffer.rewind();
        Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_BITS, intBuffer);
        Logger.info(this.getClass().getSimpleName(), "Depth buffer size: " + intBuffer.get() + " bits");

        /**
         * INITIALIZE SGRs
         */
        sgrs = new ISGR[4];
        sgrs[SGR_DEFAULT_IDX] = new SGR();
        sgrs[SGR_STEREO_IDX] = new SGRStereoscopic();
        sgrs[SGR_FOV_IDX] = new SGRFov();
        sgrs[SGR_CUBEMAP_IDX] = new SGRCubemap();
        sgr = null;

        /**
         *
         * ======= INITIALIZE RENDER COMPONENTS =======
         *
         **/
        pixelRenderSystems = new AbstractRenderSystem[3];

        renderProcesses = new Array<IRenderSystem>();

        blendNoDepthRunnable = new RenderSystemRunnable() {
            @Override
            public void run(AbstractRenderSystem renderSystem, Array<IRenderable> renderables, ICamera camera) {
                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
                Gdx.gl.glDepthMask(false);
            }
        };
        blendDepthRunnable = new RenderSystemRunnable() {
            @Override
            public void run(AbstractRenderSystem renderSystem, Array<IRenderable> renderables, ICamera camera) {
                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
                Gdx.gl.glDepthMask(true);
            }
        };

        int priority = 0;

        // POINTS
        AbstractRenderSystem pixelProc = new PixelRenderSystem(RenderGroup.POINT, priority++, alphas);
        pixelProc.setPreRunnable(blendNoDepthRunnable);

        // MODEL BACK
        AbstractRenderSystem modelBackProc = new ModelBatchRenderSystem(RenderGroup.MODEL_B, priority++, alphas, modelBatchB, false);
        modelBackProc.setPreRunnable(blendNoDepthRunnable);
        modelBackProc.setPostRunnable(new RenderSystemRunnable() {
            @Override
            public void run(AbstractRenderSystem renderSystem, Array<IRenderable> renderables, ICamera camera) {
                // This always goes at the back, clear depth buffer
                Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
            }
        });

        // VOLUMETRIC CLOUDS
        AbstractRenderSystem cloudsProc = new VolumeCloudsRenderSystem(priority++, alphas);
        cloudsProc.setPreRunnable(blendNoDepthRunnable);

        // ANNOTATIONS
        AbstractRenderSystem annotationsProc = new FontRenderSystem(RenderGroup.MODEL_B_ANNOT, priority++, alphas, spriteBatch);
        annotationsProc.setPreRunnable(blendNoDepthRunnable);
        annotationsProc.setPostRunnable(new RenderSystemRunnable() {
            @Override
            public void run(AbstractRenderSystem renderSystem, Array<IRenderable> renderables, ICamera camera) {
                // This always goes at the back, clear depth buffer
                Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
            }
        });

        // SHADER STARS
        AbstractRenderSystem shaderBackProc = new QuadRenderSystem(RenderGroup.SHADER, priority++, alphas, starShader, true);
        shaderBackProc.setPreRunnable(blendNoDepthRunnable);
        shaderBackProc.setPostRunnable(new RenderSystemRunnable() {

            private float[] positions = new float[Glow.N * 2];
            private float[] viewAngles = new float[Glow.N];
            private float[] colors = new float[Glow.N * 3];
            private Vector3 auxv = new Vector3();
            private Vector3 auxv2 = new Vector3();

            @Override
            public void run(AbstractRenderSystem renderSystem, Array<IRenderable> renderables, ICamera camera) {
                int size = renderables.size;
                if (PostProcessorFactory.instance.getPostProcessor().isLightScatterEnabled() && Particle.renderOn) {
                    // Compute light positions for light scattering or light glow
                    int lightIndex = 0;
                    float angleEdgeDeg = camera.getAngleEdge() * MathUtils.radDeg;
                    for (int i = size - 1; i >= 0; i--) {
                        IRenderable s = renderables.get(i);
                        if (s instanceof Particle) {
                            Particle p = (Particle) s;
                            if (!Constants.webgl && lightIndex < Glow.N && (GlobalConf.program.CUBEMAP360_MODE || GaiaSky.instance.cam.getDirection().angle(p.transform.position) < angleEdgeDeg)) {
                                Vector3 pos3 = p.transform.getTranslationf(auxv);
                                pos3.sub(camera.getShift().put(auxv2));
                                camera.getCamera().project(pos3);
                                // Here we **need** to use Gdx.graphics.getWidth/Height() because we use camera.project() which uses screen coordinates only
                                positions[lightIndex * 2] = auxv.x / Gdx.graphics.getWidth();
                                positions[lightIndex * 2 + 1] = auxv.y / Gdx.graphics.getHeight();
                                viewAngles[lightIndex] = p.viewAngleApparent;
                                colors[lightIndex * 3] = p.cc[0];
                                colors[lightIndex * 3 + 1] = p.cc[1];
                                colors[lightIndex * 3 + 2] = p.cc[2];
                                lightIndex++;
                            }
                        }
                    }
                    EventManager.instance.post(Events.LIGHT_POS_2D_UPDATED, lightIndex, positions, viewAngles, colors);
                } else {
                    EventManager.instance.post(Events.LIGHT_POS_2D_UPDATED, 0, positions, viewAngles, colors);
                }
            }

        });

        // LINES
        AbstractRenderSystem lineProc = getLineRenderSystem();

        // MODEL FRONT
        AbstractRenderSystem modelFrontProc = new ModelBatchRenderSystem(RenderGroup.MODEL_F, priority++, alphas, modelBatchF, false);
        modelFrontProc.setPreRunnable(blendDepthRunnable);

        // MODEL BEAM
        AbstractRenderSystem modelBeamProc = new ModelBatchRenderSystem(RenderGroup.MODEL_BEAM, priority++, alphas, modelBatchBeam, false);
        modelBeamProc.setPreRunnable(blendDepthRunnable);

        // GALAXY
        AbstractRenderSystem galaxyProc = new GalaxyRenderSystem(RenderGroup.GALAXY, priority++, alphas, modelBatchF);
        galaxyProc.setPreRunnable(blendNoDepthRunnable);

        // MODEL STARS
        AbstractRenderSystem modelStarsProc = new ModelBatchRenderSystem(RenderGroup.MODEL_S, priority++, alphas, modelBatchS, false);
        modelStarsProc.setPreRunnable(blendDepthRunnable);

        // LABELS
        AbstractRenderSystem labelsProc = new FontRenderSystem(RenderGroup.LABEL, priority++, alphas, fontBatch, fontShader);
        labelsProc.setPreRunnable(blendNoDepthRunnable);

        // SHADER SSO
        AbstractRenderSystem shaderFrontProc = new QuadRenderSystem(RenderGroup.SHADER_F, priority++, alphas, starShader, false);
        shaderFrontProc.setPreRunnable(blendNoDepthRunnable);

        // MODEL ATMOSPHERE
        AbstractRenderSystem modelAtmProc = new ModelBatchRenderSystem(RenderGroup.MODEL_F_ATM, priority++, alphas, modelBatchAtm, true) {
            @Override
            public float getAlpha(IRenderable s) {
                return alphas[ComponentType.Atmospheres.ordinal()] * (float) Math.pow(alphas[s.getComponentType().getFirstOrdinal()], 2);
            }

            @Override
            protected boolean mustRender() {
                return alphas[ComponentType.Atmospheres.ordinal()] * alphas[ComponentType.Planets.ordinal()] > 0;
            }
        };
        modelAtmProc.setPreRunnable(blendDepthRunnable);
        modelAtmProc.setPostRunnable(new RenderSystemRunnable() {
            @Override
            public void run(AbstractRenderSystem renderSystem, Array<IRenderable> renderables, ICamera camera) {
                // Clear depth buffer before rendering things up close
                Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT);
            }
        });

        // MODEL CLOSE UP
        AbstractRenderSystem modelCloseUpProc = new ModelBatchRenderSystem(RenderGroup.MODEL_CLOSEUP, priority++, alphas, modelBatchCloseUp, false);
        modelCloseUpProc.setPreRunnable(blendDepthRunnable);

        // Add components to set
        renderProcesses.add(pixelProc);
        renderProcesses.add(modelBackProc);
        //renderProcesses.add(cloudsProc);
        renderProcesses.add(annotationsProc);
        renderProcesses.add(shaderBackProc);
        renderProcesses.add(shaderFrontProc);
        renderProcesses.add(modelFrontProc);
        renderProcesses.add(modelBeamProc);
        renderProcesses.add(lineProc);
        renderProcesses.add(galaxyProc);
        renderProcesses.add(modelStarsProc);
        renderProcesses.add(labelsProc);
        renderProcesses.add(modelAtmProc);
        renderProcesses.add(modelCloseUpProc);

        EventManager.instance.subscribe(this, Events.TOGGLE_VISIBILITY_CMD, Events.PIXEL_RENDERER_UPDATE, Events.TOGGLE_STEREOSCOPIC_INFO, Events.CAMERA_MODE_CMD, Events.CUBEMAP360_CMD);

    }

    private void initSGR(ICamera camera) {
        if (camera.getNCameras() > 1) {
            // FOV mode
            sgr = sgrs[SGR_FOV_IDX];
        } else if (GlobalConf.program.STEREOSCOPIC_MODE) {
            // Stereoscopic mode
            sgr = sgrs[SGR_STEREO_IDX];
        } else if (GlobalConf.program.CUBEMAP360_MODE) {
            // 360 mode: cube map -> equirectangular map
            sgr = sgrs[SGR_CUBEMAP_IDX];
        } else {
            // Default mode
            sgr = sgrs[SGR_DEFAULT_IDX];
        }
    }

    @Override
    public void render(ICamera camera, float t, int rw, int rh, FrameBuffer fb, PostProcessBean ppb) {
        if (sgr == null)
            initSGR(camera);

        sgr.render(this, camera, t, rw, rh, fb, ppb);
    }

    /**
     * Renders the scene
     * 
     * @param camera
     *            The camera to use.
     * @param t
     *            The time in seconds since the start.
     * @param rc
     *            The render context.
     */
    public void renderScene(ICamera camera, float t, RenderContext rc) {
        // Update time difference since last update
        for (ComponentType ct : ComponentType.values()) {
            alphas[ct.ordinal()] = calculateAlpha(ct, t);
        }

        EventManager.instance.post(Events.DEBUG1, "quads: " + (render_lists.get(RenderGroup.SHADER).size() + render_lists.get(RenderGroup.SHADER_F).size()) + ", points: " + render_lists.get(RenderGroup.POINT).size() + ", labels: " + render_lists.get(RenderGroup.LABEL).size());

        int size = renderProcesses.size;
        for (int i = 0; i < size; i++) {
            IRenderSystem process = renderProcesses.get(i);
            // If we have no render group, this means all the info is already in the render system. No lists needed
            if (process.getRenderGroup() != null) {
                Array<IRenderable> l = render_lists.get(process.getRenderGroup()).toList();
                process.render(l, camera, t, rc);
            } else {
                process.render(null, camera, t, rc);
            }
        }

    }

    /**
     * This must be called when all the rendering for the current frame has
     * finished.
     */
    public void clearLists() {
        for (RenderGroup rg : RenderGroup.values()) {
            render_lists.get(rg).clear();
        }
    }

    public String[] getRenderComponents() {
        ComponentType[] comps = ComponentType.values();
        String[] res = new String[comps.length];
        int i = 0;
        for (ComponentType comp : comps) {
            res[i++] = comp.getName();
        }
        return res;
    }

    public boolean isOn(ComponentType comp) {
        return visible.contains(comp) || alphas[comp.ordinal()] > 0;
    }

    /**
     * TODO Make this faster!
     * @param comp
     * @return
     */
    public boolean isOn(GSEnumSet comp) {
        if (!visible.containsAll(comp)) {
            Iterator<ComponentType> it = comp.iterator();
            boolean vis = true;
            while (it.hasNext())
                vis = vis & alphas[it.next().ordinal()] > 0;
            return vis;
        } else {
            return true;
        }
    }

    public boolean isOn(int ordinal) {
        return visible.contains(ordinal) || alphas[ordinal] > 0;
    }

    @Override
    public void notify(Events event, final Object... data) {
        switch (event) {
        case TOGGLE_VISIBILITY_CMD:
            ComponentType ct = ComponentType.getFromName((String) data[0]);
            int idx = ct.ordinal();
            if (data.length == 3) {
                // We have the boolean
                if ((boolean) data[2])
                    visible.add(ct);
                else
                    visible.remove(ct);
                times[idx] = (long) (GaiaSky.instance.getT() * 1000f);
            } else {
                // Only toggle
                visible.toggle(ct);
                times[idx] = (long) (GaiaSky.instance.getT() * 1000f);
            }
            break;

        case PIXEL_RENDERER_UPDATE:
            Gdx.app.postRunnable(new Runnable() {

                @Override
                public void run() {
                    AbstractRenderSystem.POINT_UPDATE_FLAG = true;
                    //                    updatePixelRenderSystem();
                }

            });
            break;
        case TOGGLE_STEREOSCOPIC_INFO:
            boolean stereo = (Boolean) data[0];
            if (stereo)
                sgr = sgrs[SGR_STEREO_IDX];
            else {
                sgr = sgrs[SGR_DEFAULT_IDX];
            }
            break;
        case CUBEMAP360_CMD:
            boolean cubemap = (Boolean) data[0];
            if (cubemap)
                sgr = sgrs[SGR_CUBEMAP_IDX];
            else
                sgr = sgrs[SGR_DEFAULT_IDX];
            break;
        case CAMERA_MODE_CMD:
            CameraMode cm = (CameraMode) data[0];
            if (cm.isGaiaFov())
                sgr = sgrs[SGR_FOV_IDX];
            else {
                if (GlobalConf.program.STEREOSCOPIC_MODE)
                    sgr = sgrs[SGR_STEREO_IDX];
                else if (GlobalConf.program.CUBEMAP360_MODE)
                    sgr = sgrs[SGR_CUBEMAP_IDX];
                else
                    sgr = sgrs[SGR_DEFAULT_IDX];

            }
            break;
        }
    }

    /**
     * Computes the alpha for the given component type.
     * 
     * @param type
     *            The component type.
     * @param now
     *            The current time in seconds.
     * @return The alpha value.
     */
    private float calculateAlpha(ComponentType type, float t) {
        int ordinal = type.ordinal();
        long diff = (long) (t * 1000f) - times[ordinal];
        if (diff > GlobalConf.scene.OBJECT_FADE_MS) {
            if (visible.contains(ordinal)) {
                alphas[ordinal] = 1;
            } else {
                alphas[ordinal] = 0;
            }
            return alphas[ordinal];
        } else {
            return visible.contains(ordinal) ? MathUtilsd.lint(diff, 0, GlobalConf.scene.OBJECT_FADE_MS, 0, 1) : MathUtilsd.lint(diff, 0, GlobalConf.scene.OBJECT_FADE_MS, 1, 0);
        }
    }

    public void resize(final int w, final int h) {

        for (IRenderSystem rendSys : renderProcesses) {
            rendSys.resize(w, h);
        }

        for (ISGR sgr : sgrs) {
            sgr.resize(w, h);
        }
    }

    public void dispose() {
        for (ISGR sgr : sgrs) {
            if (sgr != null)
                sgr.dispose();
        }
    }

    private AbstractRenderSystem getLineRenderSystem() {
        AbstractRenderSystem sys = null;
        if (GlobalConf.scene.isNormalLineRenderer()) {
            // Normal
            sys = new LineRenderSystem(RenderGroup.LINE, 0, alphas);
            sys.setPreRunnable(blendDepthRunnable);
        } else {
            // Quad
            sys = new LineQuadRenderSystem(RenderGroup.LINE, 0, alphas);
            sys.setPreRunnable(blendDepthRunnable);
        }
        return sys;
    }

}
