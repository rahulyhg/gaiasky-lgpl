package gaia.cu9.ari.gaiaorbit.scenegraph;

import com.badlogic.gdx.utils.Disposable;

public interface ISceneGraphNode extends Disposable {

    public int getStarCount();

    public Object getStars();
}
