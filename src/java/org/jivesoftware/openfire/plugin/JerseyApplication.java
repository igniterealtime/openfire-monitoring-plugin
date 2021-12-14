package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.plugin.service.MonitoringAPI;

import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

public class JerseyApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        final Set<Class<?>> s = new HashSet<>();
        s.add(MonitoringAPI.class);
        s.add(RESTExceptionMapper.class);
        return s;
    }
}
