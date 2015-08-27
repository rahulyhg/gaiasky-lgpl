package gaia.cu9.ari.gaiaorbit.data.stars;

import gaia.cu9.ari.gaiaorbit.scenegraph.CelestialBody;
import gaia.cu9.ari.gaiaorbit.scenegraph.Star;
import gaia.cu9.ari.gaiaorbit.util.I18n;
import gaia.cu9.ari.gaiaorbit.util.Logger;
import gaia.cu9.ari.gaiaorbit.util.math.Vector3d;
import gaia.cu9.ari.gaiaorbit.util.parse.Parser;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

public class DigitalUniverseCatalogLoader extends AbstractCatalogLoader implements ISceneGraphLoader {
    private static final String separator = "\\s+";

    private static final float factor = 10000;
    private static final float magcut = 12f;
    private static final float distcut = 1000000f;

    @Override
    public List<CelestialBody> loadData() throws FileNotFoundException {
        List<CelestialBody> stars = new ArrayList<CelestialBody>();
        InputStream data = null;
        for (String f : files) {
            Logger.info(this.getClass().getSimpleName(), I18n.bundle.format("notif.datafile", f));
            FileHandle file = Gdx.files.internal(f);
            data = file.read();
            BufferedReader br = new BufferedReader(new InputStreamReader(data));

            try {
                //Skip first line
                br.readLine();
                String line;
                while ((line = br.readLine()) != null) {
                    //Add star
                    if (line.startsWith("#") || line.startsWith("datavar") || line.startsWith("texture") || line.startsWith("\n") || line.isEmpty()) {
                        // Skipping line
                    } else {
                        addStar(line, stars);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    br.close();
                } catch (IOException e) {
                    Logger.error(e);
                }

            }
        }
        Logger.info(this.getClass().getSimpleName(), I18n.bundle.format("notif.catalog.init", stars.size()));
        return stars;
    }

    private void addStar(String line, List<CelestialBody> stars) {
        String[] st = line.split(separator);
        float x = Parser.parseFloat(st[1]) * factor;
        float y = Parser.parseFloat(st[2]) * factor;
        float z = Parser.parseFloat(st[3]) * factor;
        float absmag = Parser.parseFloat(st[6]);
        float appmag = Parser.parseFloat(st[7]);
        float colorbv = Parser.parseFloat(st[4]);
        String name = st[19];
        Vector3d pos = new Vector3d(x, y, z);
        float dist = (float) pos.len();
        if (appmag < magcut && dist < distcut) {
            Star star = new Star(pos, absmag, appmag, colorbv, name, 0l);
            stars.add(star);
        }
    }

    @Override
    public void initialize(String[] files) {
        super.initialize(files);

    }
}
