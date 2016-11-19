package gaia.cu9.ari.gaiaorbit.render;

import com.badlogic.gdx.graphics.g3d.ModelBatch;

/**
 * To be implemented by all entities wanting to render an atmosphere.
 * @author Toni Sagrista
 *
 */
public interface IAtmosphereRenderable extends IRenderable {

    /**
     * Renders the atmosphere.
     * @param modelBatch The model batch to use.
     * @param alpha The opacity.
     * @param t The time in seconds since the start.
     * @param b Dummy parameter to differentiate this method from the IModelRenderable one.
     */
    public void render(ModelBatch modelBatch, float alpha, float t, byte b);
}