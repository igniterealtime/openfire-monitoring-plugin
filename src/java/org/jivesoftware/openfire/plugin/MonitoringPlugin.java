/*
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.plugin;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

import com.reucon.openfire.plugin.archive.impl.*;
import com.reucon.openfire.plugin.archive.xep0313.Xep0313Support1;
import com.reucon.openfire.plugin.archive.xep0313.Xep0313Support2;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.archive.ArchiveIndexer;
import org.jivesoftware.openfire.archive.ArchiveInterceptor;
import org.jivesoftware.openfire.archive.ArchiveSearcher;
import org.jivesoftware.openfire.archive.ConversationManager;
import org.jivesoftware.openfire.archive.GroupConversationInterceptor;
import org.jivesoftware.openfire.archive.MonitoringConstants;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.reporting.graph.GraphEngine;
import org.jivesoftware.openfire.reporting.stats.DefaultStatsViewer;
import org.jivesoftware.openfire.reporting.stats.MockStatsViewer;
import org.jivesoftware.openfire.reporting.stats.StatisticsModule;
import org.jivesoftware.openfire.reporting.stats.StatsEngine;
import org.jivesoftware.openfire.reporting.stats.StatsViewer;
import org.jivesoftware.openfire.reporting.util.TaskEngine;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.JiveProperties;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.defaults.DefaultPicoContainer;

import com.reucon.openfire.plugin.archive.ArchiveProperties;
import com.reucon.openfire.plugin.archive.PersistenceManager;
import com.reucon.openfire.plugin.archive.xep0136.Xep0136Support;
import com.reucon.openfire.plugin.archive.xep0313.Xep0313Support;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Openfire Monitoring plugin.
 *
 * @author Matt Tucker
 */
public class MonitoringPlugin implements Plugin {

    private static final int DEFAULT_CONVERSATION_TIMEOUT = 30; // minutes

    /**
     * The context root of the URL under which the public web endpoints are exposed.
     */
    public static final String CONTEXT_ROOT = "monitoring";

    private final String[] publicResources = new String[]
        {
            CONTEXT_ROOT + "/*"
        };

    private WebAppContext context = null;

    private MutablePicoContainer picoContainer;

    private static MonitoringPlugin instance;
    private boolean enabled = true;
    private PersistenceManager persistenceManager;
    private PersistenceManager mucPersistenceManager;
    private Xep0136Support xep0136Support;
    private Xep0313Support xep0313Support;
    private Xep0313Support1 xep0313Support1;
    private Xep0313Support2 xep0313Support2;
    private Logger Log;

    public MonitoringPlugin() {
        instance = this;

        // Enable AWT headless mode so that stats will work in headless
        // environments.
        System.setProperty("java.awt.headless", "true");

        picoContainer = new DefaultPicoContainer();
        picoContainer.registerComponentInstance(TaskEngine.getInstance());
        picoContainer.registerComponentInstance(JiveProperties.getInstance());

        // Stats and Graphing classes
        picoContainer.registerComponentImplementation(StatsEngine.class);
        picoContainer.registerComponentImplementation(GraphEngine.class);
        picoContainer.registerComponentImplementation(StatisticsModule.class);
        picoContainer.registerComponentImplementation(StatsViewer.class,
                getStatsViewerImplementation());

        // Archive classes
        picoContainer.registerComponentImplementation(ConversationManager.class);
        picoContainer.registerComponentImplementation(ArchiveInterceptor.class);
        picoContainer.registerComponentImplementation(GroupConversationInterceptor.class);
        picoContainer.registerComponentImplementation(ArchiveSearcher.class);
        picoContainer.registerComponentImplementation(ArchiveIndexer.class);
        picoContainer.registerComponentImplementation(MucIndexer.class);
        picoContainer.registerComponentImplementation(MessageIndexer.class);
    }

    private Class<? extends StatsViewer> getStatsViewerImplementation() {
        if (JiveGlobals.getBooleanProperty("stats.mock.viewer", false)) {
            return MockStatsViewer.class;
        } else {
            return DefaultStatsViewer.class;
        }
    }

    public static MonitoringPlugin getInstance() {
        return instance;
    }

    /* enabled property */
    public boolean isEnabled() {
        return this.enabled;
    }

    public PersistenceManager getPersistenceManager(JID jid) {
        Log.debug("Getting PersistenceManager for {}", jid);
        if (XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(jid) != null) {
            Log.debug("Using MucPersistenceManager");
            return mucPersistenceManager;
        }
        return persistenceManager;
    }

    /**
     * Returns the instance of a module registered with the Monitoring plugin.
     *
     * @param clazz
     *            the module class.
     * @return the instance of the module.
     */
    public Object getModule(Class<?> clazz) {
        return picoContainer.getComponentInstanceOfType(clazz);
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        Log = LoggerFactory.getLogger(MonitoringPlugin.class);

        /* Configuration */
        enabled = JiveGlobals.getBooleanProperty(ArchiveProperties.ENABLED,
                false);

        // Issue #113: Migrate full JIDs in the database
        TaskEngine.getInstance().submit( new DatabaseUpdateSplitJIDsTask() );

        persistenceManager = new JdbcPersistenceManager();
        mucPersistenceManager = new MucMamPersistenceManager();

        xep0136Support = new Xep0136Support(XMPPServer.getInstance());
        xep0136Support.start();

        xep0313Support = new Xep0313Support(XMPPServer.getInstance());
        xep0313Support.start();

        xep0313Support1 = new Xep0313Support1(XMPPServer.getInstance());
        xep0313Support1.start();

        xep0313Support2 = new Xep0313Support2(XMPPServer.getInstance());
        xep0313Support2.start();

        // Check if we Enterprise is installed and stop loading this plugin if
        // found
        File pluginDir = new File(JiveGlobals.getHomeDirectory(), "plugins");
        File[] jars = pluginDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                String fileName = pathname.getName().toLowerCase();
                return (fileName.equalsIgnoreCase("enterprise.jar"));
            }
        });
        if (jars.length > 0) {
            // Do not load this plugin since Enterprise is still installed
            System.out
                    .println("Enterprise plugin found. Stopping Monitoring Plugin");
            throw new IllegalStateException(
                    "This plugin cannot run next to the Enterprise plugin");
        }

        // Make sure that the monitoring folder exists under the home directory
        File dir = new File(JiveGlobals.getHomeDirectory() + File.separator
                + MonitoringConstants.NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        loadPublicWeb(pluginDirectory);

        picoContainer.start();
    }

    public void destroyPlugin() {

        // Issue #114: Shut down the task-engine first, to prevent tasks from being executed in a shutting-down environment.
        TaskEngine.getInstance().dispose();

        unloadPublicWeb();

        if (picoContainer != null) {
            picoContainer.stop();
            picoContainer.dispose();
            picoContainer = null;
        }
        
        xep0136Support.stop();
        xep0313Support.stop();
        xep0313Support1.stop();
        xep0313Support2.stop();
        
        instance = null;
    }


    protected void loadPublicWeb(File pluginDirectory)
    {
        Log.debug( "Excluding all public resources from the Authorization-Check filter." );
        for ( final String publicResource : publicResources )
        {
            AuthCheckFilter.addExclude(publicResource );
        }

        Log.debug( "Adding the public web sources to the same context as the one that's providing the BOSH interface." );
        context = new WebAppContext( null, pluginDirectory.getPath() + File.separator + "classes/", "/" + CONTEXT_ROOT );
        context.setClassLoader( this.getClass().getClassLoader() );

        Log.debug( "Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs)." );
        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add( new ContainerInitializer(new JettyJasperInitializer(), null ) );
        context.setAttribute( "org.eclipse.jetty.containerInitializers", initializers );
        context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager() );
        Log.debug( "Registering public web context with the embedded webserver." );
        HttpBindManager.getInstance().addJettyHandler( context );
    }

    protected void unloadPublicWeb()
    {
        if ( context != null )
        {
            Log.debug( "Removing public web context from the embedded webserver." );
            HttpBindManager.getInstance().removeJettyHandler( context );
            context.destroy();
            context = null;
        }

        for ( final String publicResource : publicResources )
        {
            Log.debug( "Removing Authorization-Check filter exemptions." );
            AuthCheckFilter.removeExclude( publicResource );
        }
    }
}
