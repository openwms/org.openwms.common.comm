/*
 * Copyright 2019 Heiko Scherrer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openwms.common.comm.osip.ack;

import org.openwms.common.comm.osip.Payload;
import org.openwms.common.comm.osip.ResponseHeader;

import java.io.Serializable;
import java.util.Date;

/**
 * A AckMessage.
 *
 * @author <a href="mailto:hscherrer@interface21.io">Heiko Scherrer</a>
 */
public class AckMessage extends Payload implements Serializable {

    /** Message identifier {@value}. */
    public static final String IDENTIFIER = "ACK_";

    private AckMessage(Builder builder) {
        setHeader(builder.header);
        setErrorCode(builder.errorCode);
        setCreated(builder.created);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMessageIdentifier() {
        return IDENTIFIER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWithoutReply() {
        return true;
    }


    public static final class Builder {
        private ResponseHeader header;
        private String errorCode;
        private Date created;

        private Builder() {
        }

        public Builder header(ResponseHeader val) {
            header = val;
            return this;
        }

        public Builder errorCode(String val) {
            errorCode = val;
            return this;
        }

        public Builder created(Date val) {
            created = val;
            return this;
        }

        public AckMessage build() {
            return new AckMessage(this);
        }
    }
}
