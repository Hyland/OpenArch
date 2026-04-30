package org.nuxeo.runtime.mongodb.quartz;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.mongodb.MongoDBConnectionService;

public class DefaultMongoDBJobStore extends com.novemberain.quartz.mongodb.MongoDBJobStore {

    public DefaultMongoDBJobStore() {
        super(Framework.getService(MongoDBConnectionService.class).getClient());
    }
}
