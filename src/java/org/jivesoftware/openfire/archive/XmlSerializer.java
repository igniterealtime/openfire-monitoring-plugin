/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The XmlSerializer assists in converting selected Monitoring plugin objects from and to XML representation.
 */
public class XmlSerializer {
    private static final Logger Log = LoggerFactory.getLogger(XmlSerializer.class);
    private static final Class<?>[] classesToBind = {
        ArrayList.class,
        HashMap.class,
        HashSet.class,
        ConcurrentHashMap.class,
        Conversation.class,
        UserParticipations.class,
        ConversationParticipation.class,
        ConversationEvent.class
    };

    private static XmlSerializer instance;
    public static synchronized XmlSerializer getInstance() {
        if (instance == null) {
            instance = new XmlSerializer();
        }

        return instance;
    }

    private final Marshaller marshaller;
    private final Unmarshaller unmarshaller;

    private XmlSerializer() {
        Log.trace("Binding classes: {}", Arrays.stream(classesToBind).map(Class::toString).collect(Collectors.joining(", ")));
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(classesToBind);
            this.marshaller = jaxbContext.createMarshaller();
            this.unmarshaller = jaxbContext.createUnmarshaller();
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Unable to create xml serializer using classes " + Arrays.stream(classesToBind).map(Class::toString).collect(Collectors.joining(", ")), e);
        }
    }

    /**
     * Convert some object to its XML representation.
     *
     * @param object The object to convert.
     * @return The resulting XML representation.
     * @throws IOException On any issue that occurs when marshalling this instance to XML.
     */
    @Nonnull
    public String marshall(@Nullable Object object) throws IOException {
        if (object == null) {
            return "";
        } else {
            StringWriter writer = new StringWriter();

            try {
                this.marshaller.marshal(object, writer);
            } catch (JAXBException e) {
                throw new IOException("Object could not be marshalled into an XML format: " + object, e);
            }

            return writer.toString();
        }
    }

    /**
     * Converts an XML representation back to an object.
     * @param object The XML representation from which the object needs to be rebuilt.
     * @return The resulting object.
     */
    @Nullable
    public Object unmarshall(@Nullable String object) throws IOException {
        if (object != null && !"".equals(object)) {
            StringReader reader = new StringReader(object);

            try {
                return this.unmarshaller.unmarshal(reader);
            } catch (JAXBException e) {
                throw new IOException("XML value could not be unmarshalled into an object: " + object, e);
            }
        } else {
            return null;
        }
    }

    /**
     * This simple JAXB adapter allows for converting between JID and String, without the need for annotations to be
     * placed in the JID class.
     */
    static class JidAdapter extends XmlAdapter<String, JID> {
        @Override
        public JID unmarshal(final String v) {
            return new JID(v);
        }
        @Override
        public String marshal(JID v) {
            if (v.getResource() != null) {
                return v.toFullJID();
            } else {
                return v.toBareJID();
            }
        }
    }
}
