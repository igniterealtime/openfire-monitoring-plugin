package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.plugin.service.LogAPI;

import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

public class JerseyApplicationPublic extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        final Set<Class<?>> s = new HashSet<>();
        s.add(LogAPI.class);
        s.add(RESTExceptionMapper.class);
        return s;
    }
}
