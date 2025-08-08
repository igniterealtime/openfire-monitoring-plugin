package com.reucon.openfire.plugin.archive.xep;

import com.reucon.openfire.plugin.archive.xep0313.Xep0313Support;
import com.reucon.openfire.plugin.archive.xep0313.Xep0313Support1;
import com.reucon.openfire.plugin.archive.xep0313.Xep0313Support2;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MultiUserChatManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TimerTask;

public class XEP0313TimerTask extends TimerTask {
    private static final Logger Log = LoggerFactory.getLogger(XEP0313TimerTask.class);

    private final Xep0313Support xep0313Support;
    private final Xep0313Support1 xep0313Support1;
    private final Xep0313Support2 xep0313Support2;

    public XEP0313TimerTask(Xep0313Support xep0313Support, Xep0313Support1 xep0313Support1, Xep0313Support2 xep0313Support2) {
        this.xep0313Support = xep0313Support;
        this.xep0313Support1 = xep0313Support1;
        this.xep0313Support2 = xep0313Support2;
    }

    @Override
    public void run() {
        MultiUserChatManager manager = XMPPServer.getInstance().getMultiUserChatManager();
        for (MultiUserChatService mucService : manager.getMultiUserChatServices()) {
            if(mucService instanceof MultiUserChatServiceImpl mucImpl){
                Set<String> features = new HashSet<>();
                Iterator<String> iterator = mucImpl.getFeatures(null, null, null);
                while (iterator.hasNext()) {
                    features.add(iterator.next());
                }
                if(!features.contains(xep0313Support.namespace)){
                    xep0313Support.iqHandlers.forEach(mucService::addIQHandler);
                    mucService.addExtraFeature(xep0313Support.namespace);
                    Log.info("Step 1: New MultiUserChatService {} had been added feature {}", mucService.getServiceName(), xep0313Support.namespace);
                }
                if(!features.contains(xep0313Support1.namespace)){
                    xep0313Support1.iqHandlers.forEach(mucService::addIQHandler);
                    mucService.addExtraFeature(xep0313Support1.namespace);
                    Log.info("Step 2: New MultiUserChatService {} had been added feature {}", mucService.getServiceName(), xep0313Support1.namespace);
                }
                if(!features.contains(xep0313Support2.namespace)){
                    xep0313Support2.iqHandlers.forEach(mucService::addIQHandler);
                    mucService.addExtraFeature(xep0313Support2.namespace);
                    Log.info("Step 3: New MultiUserChatService {} had been added feature {}", mucService.getServiceName(), xep0313Support2.namespace);
                }
            }

        }
    }
}
