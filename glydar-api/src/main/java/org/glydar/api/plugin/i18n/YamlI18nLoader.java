package org.glydar.api.plugin.i18n;

import java.io.Reader;
import java.util.Collections;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public class YamlI18nLoader implements I18nFormatLoader {

    @Override
    public String[] getExtensions() {
        return new String[] { "yml", "yaml" };
    }

    @Override
    public Map<?, ?> load(Reader reader) {
        Yaml yaml = new Yaml();
        Object obj = yaml.load(reader);
        if (!(obj instanceof Map)) {
            return Collections.<String, String> emptyMap();
        }

        return (Map<?, ?>) obj;
    }
}
