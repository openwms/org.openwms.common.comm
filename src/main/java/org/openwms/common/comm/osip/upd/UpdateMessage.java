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

import org.openwms.common.comm.osip.Payload;
import org.openwms.common.comm.osip.ResponseHeader;
import org.openwms.common.comm.spi.FieldLengthProvider;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A UpdateMessage reflects the OSIP UPD telegram type and is used to book a {@code TransportUnit}
 * on a {@code Location}.
 *
 * @author <a href="mailto:hscherrer@openwms.org">Heiko Scherrer</a>
 */
public class UpdateMessage extends Payload implements Serializable {

    /** Message identifier {@value} . */
    public static final String IDENTIFIER = "UPD_";
    private String barcode;
    private String actualLocation;

    /*~------------ Constructors ------------*/
    private UpdateMessage(Builder builder) {
        barcode = builder.barcode;
        actualLocation = builder.actualLocation;
    }

    protected UpdateMessage() {
    }

    /*~------------ Accessors ------------*/
    String getBarcode() {
        return barcode;
    }

    void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    String getActualLocation() {
        return actualLocation;
    }

    void setActualLocation(String actualLocation) {
        this.actualLocation = actualLocation;
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
        return true;
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

        public UpdateMessage.Builder withHeader(ResponseHeader header) {
            this.header = header;
            return this;
        }

        public Builder withBarcode(String barcode) {
            this.barcode = barcode;
            return this;
        }

        public Builder withActualLocation(String actualLocation) {
            this.actualLocation = String.join("/",
                    actualLocation.split("(?<=\\G.{" + provider.locationIdLength() / provider.noLocationIdFields() + "})"));
            return this;
        }

        public Builder withErrorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder withCreateDate(String createDate, String pattern) throws ParseException {
            this.created = new SimpleDateFormat(pattern).parse(createDate);
            return this;
        }

        public UpdateMessage build() {
            UpdateMessage res = new UpdateMessage(this);
            res.setHeader(this.header);
            res.setBarcode(this.barcode);
            res.setActualLocation(this.actualLocation);
            res.setErrorCode(this.errorCode);
            res.setCreated(this.created);
            return res;
        }
    }

    /*~------------ Overrides ------------*/
    /**
     * {@inheritDoc}
     *
     * Include all fields.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        UpdateMessage that = (UpdateMessage) o;
        return Objects.equals(barcode, that.barcode) && Objects.equals(actualLocation, that.actualLocation);
    }

    /**
     * {@inheritDoc}
     *
     * Include all fields.
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), barcode, actualLocation);
    }

    /**
     * {@inheritDoc}
     *
     * Include all fields.
     */
    @Override
    public String toString() {
        return new StringJoiner(", ", UpdateMessage.class.getSimpleName() + "[", "]").add("barcode='" + barcode + "'").add("actualLocation='" + actualLocation + "'").toString();
    }
}
