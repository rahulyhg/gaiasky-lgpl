package gaia.cu9.ari.gaiaorbit.scenegraph;

import java.util.Random;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

import gaia.cu9.ari.gaiaorbit.GaiaSky;
import gaia.cu9.ari.gaiaorbit.event.EventManager;
import gaia.cu9.ari.gaiaorbit.event.Events;
import gaia.cu9.ari.gaiaorbit.event.IObserver;
import gaia.cu9.ari.gaiaorbit.render.ComponentType;
import gaia.cu9.ari.gaiaorbit.render.ILineRenderable;
import gaia.cu9.ari.gaiaorbit.render.IPointRenderable;
import gaia.cu9.ari.gaiaorbit.render.IRenderable;
import gaia.cu9.ari.gaiaorbit.render.SceneGraphRenderer;
import gaia.cu9.ari.gaiaorbit.render.system.LineRenderSystem;
import gaia.cu9.ari.gaiaorbit.util.Constants;
import gaia.cu9.ari.gaiaorbit.util.GSEnumSet;
import gaia.cu9.ari.gaiaorbit.util.GlobalConf;
import gaia.cu9.ari.gaiaorbit.util.color.ColourUtils;
import gaia.cu9.ari.gaiaorbit.util.concurrent.ThreadIndexer;
import gaia.cu9.ari.gaiaorbit.util.math.Vector3d;
import gaia.cu9.ari.gaiaorbit.util.time.ITimeFrameProvider;
import gaia.cu9.ari.gaiaorbit.util.tree.OctreeNode;
import net.jafama.FastMath;

/**
 * A point particle which may represent a star, a galaxy, etc.
 * 
 * @author Toni Sagrista
 *
 */
public class Particle extends CelestialBody implements IPointRenderable, ILineRenderable {

    private static final float DISC_FACTOR = 1.5f;
    private static final float LABEL_FACTOR = Constants.webgl ? 3f : 1f;

    private static Random rnd = new Random();

    protected static float thpointTimesFovfactor;
    protected static float thupOverFovfactor;
    protected static float thdownOverFovfactor;
    protected static float innerRad;
    protected static float fovFactor;
    protected static ParamUpdater paramUpdater;

    protected static class ParamUpdater implements IObserver {
        public ParamUpdater() {
            super();
            EventManager.instance.subscribe(this, Events.FOV_CHANGE_NOTIFICATION, Events.STAR_POINT_SIZE_CMD);
        }

        @Override
        public void notify(Events event, Object... data) {
            switch (event) {
            case FOV_CHANGE_NOTIFICATION:
                fovFactor = (Float) data[1];
                thpointTimesFovfactor = (float) GlobalConf.scene.STAR_THRESHOLD_POINT * fovFactor;
                thupOverFovfactor = (float) Constants.THRESHOLD_UP / fovFactor;
                thdownOverFovfactor = (float) Constants.THRESHOLD_DOWN / fovFactor;
                break;
            case STAR_POINT_SIZE_CMD:
                innerRad = 0.004f * DISC_FACTOR + (Float) data[0] * 0.009f;
                break;
            }
        }
    }

    static {
        if (GaiaSky.instance != null) {
            fovFactor = GaiaSky.instance.getCameraManager().getFovFactor();
        } else {
            fovFactor = 1f;
        }
        thpointTimesFovfactor = (float) GlobalConf.scene.STAR_THRESHOLD_POINT * fovFactor;
        thupOverFovfactor = (float) Constants.THRESHOLD_UP / fovFactor;
        thdownOverFovfactor = (float) Constants.THRESHOLD_DOWN / fovFactor;
        float psize = GlobalConf.scene.STAR_POINT_SIZE < 0 ? 8 : GlobalConf.scene.STAR_POINT_SIZE;
        innerRad = 0.004f * DISC_FACTOR + psize * 0.009f;
        paramUpdater = new ParamUpdater();
    }

    @Override
    public double THRESHOLD_NONE() {
        return (float) GlobalConf.scene.STAR_THRESHOLD_NONE;
    }

    @Override
    public double THRESHOLD_POINT() {
        return (float) GlobalConf.scene.STAR_THRESHOLD_POINT;
    }

    @Override
    public double THRESHOLD_QUAD() {
        return (float) GlobalConf.scene.STAR_THRESHOLD_QUAD;
    }

    /** Must be updated every cycle **/
    public static boolean renderOn = false;

    /** Proper motion in cartesian coordinates [U/yr] **/
    public Vector3 pm, pmSph;

    /**
     * Source of this star:
     * <ul>
     * <li>-1: Unknown</li>
     * <li>1: Gaia</li>
     * <li>2: Hipparcos (HYG)</li>
     * <li>3: Tycho</li>
     * </ul>
     */
    public byte catalogSource = -1;

    public double computedSize;
    double radius;
    boolean randomName = false;
    boolean hasPm = false;

    /**
     * Object server properties
     */

    /** The id of the octant it belongs to, if any **/
    public long octantId;
    /** Its page **/
    public OctreeNode octant;

    /**
     * Particle type 90 - real star 92 - virtual particle
     */
    public int type = 90;
    public int nparticles = 1;

    public Particle() {
        this.parentName = ROOT_NAME;
    }

    /**
     * Creates a new star.
     * 
     * @param pos
     *            Cartesian position, in equatorial coordinates and in internal
     *            units.
     * @param appmag
     *            Apparent magnitude.
     * @param absmag
     *            Absolute magnitude.
     * @param colorbv
     *            The B-V color index.
     * @param name
     *            The label or name.
     * @param starid
     *            The star unique id.
     */
    public Particle(Vector3d pos, float appmag, float absmag, float colorbv, String name, long starid) {
        this();
        this.pos = pos;
        this.name = name;
        this.appmag = appmag;
        this.absmag = absmag;
        this.colorbv = colorbv;
        this.id = starid;

        if (this.name == null) {
            randomName = true;
            this.name = "star_" + rnd.nextInt(10000000);
        }
        this.pm = new Vector3();
        this.pmSph = new Vector3();
    }

    public Particle(Vector3d pos, float appmag, float absmag, float colorbv, String name, float ra, float dec, long starid) {
        this(pos, appmag, absmag, colorbv, name, starid);
        this.posSph = new Vector2(ra, dec);

    }

    public Particle(Vector3d pos, Vector3 pm, Vector3 pmSph, float appmag, float absmag, float colorbv, String name, float ra, float dec, long starid) {
        this(pos, appmag, absmag, colorbv, name, starid);
        this.posSph = new Vector2(ra, dec);
        this.pm.set(pm);
        this.pmSph.set(pmSph);
        this.hasPm = this.pm.len2() != 0;

    }

    @Override
    public void initialize() {
        setDerivedAttributes();
        ct = GSEnumSet.of(ComponentType.Galaxies);
        // Relation between our star size and actual star size (normalized for the Sun, 1391600 Km of diameter
        radius = size * Constants.STAR_SIZE_FACTOR;
    }

    public float getActualRadius() {
        return (float) radius;
    }

    private void setDerivedAttributes() {
        this.flux = (float) Math.pow(10, -absmag / 2.5f);
        setRGB(colorbv);

        // Calculate size - This contains arbitrary boundary values to make things nice on the render side
        size = (float) Math.max(Math.min((Math.pow(flux, 0.5f) * Constants.PC_TO_U * 0.16f), 1e8f), 0.6e6f) / DISC_FACTOR;
        computedSize = 0;
    }

    @Override
    public void update(ITimeFrameProvider time, final Transform parentTransform, ICamera camera) {
        update(time, parentTransform, camera, 1f);
    }

    /**
     * Re-implementation of update method of {@link CelestialBody} and
     * {@link SceneGraphNode}.
     */
    @Override
    public void update(ITimeFrameProvider time, final Transform parentTransform, ICamera camera, float opacity) {
        if (appmag <= GlobalConf.runtime.LIMIT_MAG_RUNTIME) {
            this.opacity = opacity;
            transform.position.set(parentTransform.position).add(pos);
            //            if (hasPm) {
            //                Vector3 pmv = new Vector3(pm).scl((float) Constants.S_TO_Y).scl((float) AstroUtils.getMsSinceJ2015(time.getTime()) / 1000f);
            //                transform.position.add(pmv);
            //            }
            distToCamera = transform.position.len();

            if (!copy) {
                // TODO Very ugly!
                if (ModelBody.closestCamStar == null || ModelBody.closestCamStar.distToCamera > distToCamera)
                    //if (ModelBody.closestCamStar == null || ModelBody.closestCamStar.distToCamera > distToCamera) 
                    ModelBody.closestCamStar = this;

                addToRender(this, RenderGroup.POINT);

                viewAngle = (float) (radius / distToCamera) / camera.getFovFactor();
                viewAngleApparent = viewAngle * GlobalConf.scene.STAR_BRIGHTNESS;

                addToRenderLists(camera);
            }

            // Compute nested
            if (children != null) {
                for (int i = 0; i < children.size; i++) {
                    SceneGraphNode child = children.get(i);
                    child.update(time, parentTransform, camera, opacity);
                }
            }
            if (GlobalConf.scene.COMPUTE_GAIA_SCAN)
                camera.computeGaiaScan(time, this);
        }
    }

    @Override
    protected void addToRenderLists(ICamera camera) {
        if (camera.getCurrent() instanceof FovCamera) {
            // Render as point, do nothing
        } else {

            if (viewAngleApparent >= thpointTimesFovfactor) {
                addToRender(this, RenderGroup.SHADER);
            }
            if (viewAngleApparent >= thpointTimesFovfactor / GlobalConf.scene.PM_NUM_FACTOR && this.hasPm) {
                addToRender(this, RenderGroup.LINE);
            }
        }
        if (renderText() && camera.isVisible(GaiaSky.instance.time, this)) {
            addToRender(this, RenderGroup.LABEL);
        }

    }

    protected boolean addToRender(IRenderable renderable, RenderGroup rg) {
        if (renderOn) {
            SceneGraphRenderer.render_lists.get(rg).add(renderable, ThreadIndexer.i());
            return true;
        }
        return false;
    }

    @Override
    public void render(ModelBatch modelBatch, float alpha, float t) {
        // Void
    }

    /**
     * Sets the color
     * 
     * @param bv
     *            B-V color index
     */
    private void setRGB(float bv) {
        if (cc == null)
            cc = ColourUtils.BVtoRGB(bv);
        setColor2Data();
    }

    @Override
    public float getInnerRad() {
        return innerRad;
    }

    @Override
    public float getRadius() {
        return (float) radius;
    }

    public boolean isStar() {
        return true;
    }

    @Override
    public boolean renderText() {
        return computedSize > 0 && GaiaSky.instance.isOn(ComponentType.Labels) && viewAngleApparent >= (TH_OVER_FACTOR / GaiaSky.instance.cam.getFovFactor());
    }

    @Override
    public float labelSizeConcrete() {
        return (float) computedSize * LABEL_FACTOR;
    }

    @Override
    public float textScale() {
        return (float) FastMath.atan(labelMax()) * labelFactor() * 4e2f;
    }

    @Override
    protected float labelFactor() {
        return 1.3e-1f;
    }

    @Override
    protected float labelMax() {
        return 0.015f;
    }

    public float getFuzzyRenderSize(ICamera camera) {
        computedSize = this.size;
        if (viewAngle > thdownOverFovfactor) {
            double dist = distToCamera;
            if (viewAngle > thupOverFovfactor) {
                dist = (float) radius / Constants.THRESHOLD_UP;
            }
            computedSize = this.size * (dist / this.radius) * Constants.THRESHOLD_DOWN;
        }
        computedSize *= GlobalConf.scene.STAR_BRIGHTNESS;

        return (float) computedSize;
    }

    @Override
    public void doneLoading(AssetManager manager) {
    }

    @Override
    public void updateLocalValues(ITimeFrameProvider time, ICamera camera) {
    }

    @Override
    public int getStarCount() {
        return 1;
    }

    @Override
    public Object getStars() {
        return this;
    }

    @Override
    public <T extends SceneGraphNode> T getSimpleCopy() {
        Particle copy = (Particle) super.getSimpleCopy();
        copy.pm = this.pm;
        copy.hasPm = this.hasPm;
        return (T) copy;
    }

    /**
     * Line renderer. Renders proper motion
     * 
     * @param renderer
     * @param camera
     * @param alpha
     */
    @Override
    public void render(LineRenderSystem renderer, ICamera camera, float alpha) {
        Vector3 campos = aux3f1.get();
        Vector3 p1 = transform.position.setVector3(aux3f2.get());
        Vector3 ppm = aux3f3.get().set(pm).scl(GlobalConf.scene.PM_LEN_FACTOR);
        Vector3 p2 = aux3f4.get().set(p1).add(ppm);
        camera.getPos().setVector3(campos);

        // Mualpha -> red channel
        // Mudelta -> green channel
        // Radvel  -> blue channel
        // Min value per channel = 0.2
        final double mumin = -80;
        final double mumax = 80;
        final double maxmin = mumax - mumin;
        renderer.addLine(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, (float) ((pmSph.x - mumin) / maxmin) * 0.8f + 0.2f, (float) ((pmSph.y - mumin) / maxmin) * 0.8f + 0.2f, (float) pmSph.z * 0.8f + 0.2f, alpha);
    }

    protected float getThOverFactorScl() {
        return fovFactor;
    }

}
