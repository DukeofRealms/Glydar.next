package org.glydar.api.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;
import java.util.logging.Level;

import org.glydar.api.Glydar;
import org.glydar.api.logging.GlydarLogger;
import org.glydar.api.plugin.command.CommandSet;
import org.glydar.api.plugin.i18n.I18n;
import org.glydar.api.plugin.i18n.I18nLoader;
import org.glydar.api.plugin.i18n.I18nTarget;

import com.google.common.collect.ImmutableList;

public abstract class Plugin implements CommandSet, I18nTarget {

    private PluginLoader loader;
    private GlydarLogger logger;
    private boolean      enabled = false;

    public void onEnable() {
    }

    public void onDisable() {
    }

    public abstract String getVersion();

    public abstract String getName();

    public String getAuthor() {
        return null;
    }

    public GlydarLogger getLogger() {
        return logger;
    }

    public PluginLoader getLoader() {
        return loader;
    }

    @Override
    public Iterable<URL> getI18nLocations(String filename) {
        ImmutableList.Builder<URL> builder = ImmutableList.builder();

        // URLClassLoader cl = loader.getClassLoader(this);
        // builder.add(cl.getResource(filename));

        File userLocation = new File(getConfigFolder(), filename);
        if (userLocation.exists()) {
            try {
                builder.add(userLocation.toURI().toURL());
            }
            catch (MalformedURLException exc) {
                getLogger().log(Level.WARNING, "Unable to convert i18n filepath to an url for " + filename, exc);
            }
        }

        return builder.build();
    }

    /**
     * Get an {@link I18n} instance for the given name and the locales defined
     * in the server configuration. Localization files are looked for in the jar
     * and the config folder of this plugin.
     */
    public I18n getI18n(String name) {
        // TODO: Get locales from the server config
        I18nLoader i18nloader = new I18nLoader(this, new Locale[0]);
        return i18nloader.load(name);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean b) {
        if (enabled && b)
            return;
        else if (!enabled && !b)
            return;
        else if (b) {
            enabled = b;
            onEnable();
        }
        else {
            enabled = b;
            onDisable();
        }
    }

    public File getConfigFolder() {
        File file = new File("config/" + getName());
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public InputStream getResource(String name) {
        URLClassLoader cl = loader.getClassLoader(this);
        return cl.getResourceAsStream(name);
    }

    public void saveResource(String name) {
        File file = Glydar.getConfigFolder().resolve(name).toFile();
        saveResource(name, file);
    }

    public void saveResource(String name, File file) {
        InputStream in = null;
        OutputStream out = null;
        try {
            file.createNewFile();
            in = getResource(name);
            out = new FileOutputStream(file);
            if (in == null)
                throw new PluginException("Could not find resource " + file.getName());
            byte[] buffer = new byte[1024];
            int len = in.read(buffer);
            while (len != -1) {
                out.write(buffer, 0, len);
                len = in.read(buffer);
            }
        }
        catch (Exception e) {
            logger.warning("Error while saving file " + file.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return;
        }
        finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            }
            catch (IOException e) {
            }
        }
    }

    protected final void initialize(PluginLoader loader, GlydarLogger logger) {
        this.logger = logger;
        this.loader = loader;
    }
}
