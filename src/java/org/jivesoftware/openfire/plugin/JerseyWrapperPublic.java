package org.jivesoftware.openfire.plugin;

import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import org.jivesoftware.openfire.plugin.service.LogAPI;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import java.util.HashMap;
import java.util.Map;

/**
 * The Class JerseyWrapper.
 */
public class JerseyWrapperPublic extends ServletContainer {

    private static final long serialVersionUID = 4807992231163442643L;

    /** The Constant RESOURCE_CONFIG_CLASS_KEY. */
    private static final String RESOURCE_CONFIG_CLASS_KEY = "com.sun.jersey.config.property.resourceConfigClass";

    /** The Constant RESOURCE_CONFIG_CLASS. */
    private static final String RESOURCE_CONFIG_CLASS = "com.sun.jersey.api.core.PackagesResourceConfig";

    /** The config. */
    private static Map<String, Object> config;

    /** The prc. */
    private static PackagesResourceConfig prc;

    static {
        config = new HashMap<String, Object>();
        config.put(RESOURCE_CONFIG_CLASS_KEY, RESOURCE_CONFIG_CLASS);
        config.put("com.sun.jersey.api.json.POJOMappingFeature", true);
        prc = new PackagesResourceConfig(JerseyWrapperPublic.class.getPackage().getName());
        prc.setPropertiesAndFeatures(config);

        prc.getClasses().add(LogAPI.class);

        prc.getClasses().add(RESTExceptionMapper.class);
    }

    /**
     * Instantiates a new jersey wrapper.
     */
    public JerseyWrapperPublic() {
        super(prc);
    }

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
    }

    @Override
    public void destroy() {
        super.destroy();
    }
}
