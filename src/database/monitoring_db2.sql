
INSERT INTO ofVersion (name, version) VALUES ('monitoring', 9);

CREATE TABLE ofConversation (
  conversationID        INTEGER      NOT NULL,
  roomID                INTEGER,
  room                  VARCHAR(512),
  isExternal            INTEGER      NOT NULL,
  startDate             BIGINT       NOT NULL,
  lastActivity          BIGINT       NOT NULL,
  messageCount          INTEGER      NOT NULL,
  CONSTRAINT ofConversation_pk PRIMARY KEY (conversationID)
);
CREATE INDEX ofConversation_ext_idx   ON ofConversation (isExternal);
CREATE INDEX ofConversation_start_idx ON ofConversation (startDate);
CREATE INDEX ofConversation_last_idx  ON ofConversation (lastActivity);
CREATE INDEX ofConversation_room_idx  ON ofConversation (roomID);

CREATE TABLE ofConParticipant (
  conversationID       INTEGER       NOT NULL,
  joinedDate           BIGINT        NOT NULL,
  leftDate             BIGINT,
  bareJID              VARCHAR(255)  NOT NULL,
  jidResource          VARCHAR(255)  NOT NULL,
  nickname             VARCHAR(255)
);
CREATE INDEX entConPar_con_idx ON ofConParticipant (conversationID, bareJID, jidResource, joinedDate);
CREATE INDEX entConPar_jid_idx ON ofConParticipant (bareJID);

CREATE TABLE ofMessageArchive (
   messageID		 BIGINT			 NULL,
   conversationID    INTEGER         NOT NULL,
   fromJID           VARCHAR(1024)   NOT NULL,
   fromJIDResource   VARCHAR(255)    NULL,
   toJID             VARCHAR(1024)   NOT NULL,
   toJIDResource     VARCHAR(255)    NULL,
   sentDate          BIGINT          NOT NULL,
   stanza			 LONG VARCHAR    NULL,
   body              LONG VARCHAR    NULL,
   isPMforJID        VARCHAR(1024)   NULL
);
CREATE INDEX ofMessageArchive_con_idx ON ofMessageArchive (conversationID);
CREATE INDEX ofMessageArchive_fromjid_idx ON ofMessageArchive (fromJID);
CREATE INDEX ofMessageArchive_tojid_idx ON ofMessageArchive (toJID);
CREATE INDEX ofMessageArchive_sent_idx ON ofMessageArchive (sentDate);
CREATE INDEX ofMessageArchive_pm_idx ON ofMessageArchive (isPMforJID);

CREATE TABLE ofRRDs (
   id            VARCHAR(100)        NOT NULL,
   updatedDate   BIGINT              NOT NULL,
   bytes         BLOB                NULL,
   CONSTRAINT ofRRDs_pk PRIMARY KEY (id)
);

