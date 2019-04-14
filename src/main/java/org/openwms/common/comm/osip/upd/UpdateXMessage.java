/*
 * Copyright 2018 Heiko Scherrer
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
package org.openwms.common.comm.osip.upd;

import org.openwms.common.comm.osip.ResponseHeader;
import org.openwms.common.comm.spi.FieldLengthProvider;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A UpdateXMessage reflects the OSIP UPDX telegram type and is used to book a {@code TransportUnit}
 * on a {@code Location} with acknowledgement.
 *
 * @author <a href="mailto:hscherrer@openwms.org">Heiko Scherrer</a>
 */
public class UpdateXMessage extends UpdateMessage implements Serializable {

    /** Message identifier {@value} . */
    public static final String IDENTIFIER = "UPDX";

    /*~------------ Overrides ------------*/
    private UpdateXMessage(UpdateXMessage.Builder builder) {
        setBarcode(builder.barcode);
        setActualLocation(builder.actualLocation);
        setCreated(builder.created);
        setErrorCode(builder.errorCode);
        setHeader(builder.header);
    }

    protected UpdateXMessage() {
    }

    /*~------------ Overrides ------------*/
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
        return false;
    }

    /*~------------ Builders ------------*/
    public static final class Builder {
        private ResponseHeader header;
        private String barcode;
        private String actualLocation;
        private String errorCode;
        private Date created;
        private final FieldLengthProvider provider;

        public Builder(FieldLengthProvider provider) {
            this.provider = provider;
        }

        public UpdateXMessage.Builder withHeader(ResponseHeader header) {
            this.header = header;
            return this;
        }

        public UpdateXMessage.Builder withBarcode(String barcode) {
            this.barcode = barcode;
            return this;
        }

        public UpdateXMessage.Builder withActualLocation(String actualLocation) {
            this.actualLocation = String.join("/",
                    actualLocation.split("(?<=\\G.{" + provider.locationIdLength() / provider.noLocationIdFields() + "})"));
            return this;
        }

        public UpdateXMessage.Builder withErrorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public UpdateXMessage.Builder withCreateDate(String createDate, String pattern) throws ParseException {
            this.created = new SimpleDateFormat(pattern).parse(createDate);
            return this;
        }

        public UpdateXMessage build() {
            UpdateXMessage res = new UpdateXMessage(this);
            res.setHeader(this.header);
            res.setBarcode(this.barcode);
            res.setActualLocation(this.actualLocation);
            res.setErrorCode(this.errorCode);
            res.setCreated(this.created);
            return res;
        }
    }
}
