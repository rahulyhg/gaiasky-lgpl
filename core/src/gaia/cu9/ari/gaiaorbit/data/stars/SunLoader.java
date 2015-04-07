package gaia.cu9.ari.gaiaorbit.data.stars;

import gaia.cu9.ari.gaiaorbit.scenegraph.SceneGraphNode;
import gaia.cu9.ari.gaiaorbit.scenegraph.Star;
import gaia.cu9.ari.gaiaorbit.util.math.Vector3d;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Adds the sun manually
 * @author Toni Sagrista
 *
 */
public class SunLoader implements ICatalogLoader {

    @Override
    public List<? extends SceneGraphNode> loadCatalog() throws FileNotFoundException {
	List<Star> result = new ArrayList<Star>(1);
	/** ADD SUN MANUALLY **/
	Star sun = new Star(new Vector3d(0, 0, 0), 4.83f, 4.83f, 0.656f, "Sol", System.currentTimeMillis());
	sun.initialize();
	result.add(sun);
	return result;
    }

    @Override
    public void initialize(Properties p) {

    }

}