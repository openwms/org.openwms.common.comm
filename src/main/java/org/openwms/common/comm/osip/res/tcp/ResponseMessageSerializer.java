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
package org.openwms.common.comm.osip.res.tcp;

import org.openwms.common.comm.MessageMismatchException;
import org.openwms.common.comm.ParserUtils;
import org.openwms.common.comm.config.Driver;
import org.openwms.common.comm.osip.OSIPComponent;
import org.openwms.common.comm.osip.OSIPHeader;
import org.openwms.common.comm.osip.OSIPSerializer;
import org.openwms.common.comm.osip.res.ResponseMessage;

import java.text.SimpleDateFormat;

import static java.lang.String.format;
import static org.openwms.common.comm.ParserUtils.padRight;

/**
 * A ResponseMessageSerializer.
 *
 * @author <a href="mailto:hscherrer@openwms.org">Heiko Scherrer</a>
 */
@OSIPComponent
class ResponseMessageSerializer extends OSIPSerializer<ResponseMessage> {

    public ResponseMessageSerializer(Driver driver) {
        super(driver);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMessageIdentifier() {
        return ResponseMessage.IDENTIFIER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String serialize(ResponseMessage obj) {
        short maxTelegramLength = getDriver().getOsip().getTelegramLength();
        OSIPHeader header = new OSIPHeader.Builder()
                .sync(getDriver().getOsip().getSyncField())
                .messageLength(maxTelegramLength)
                .sender(obj.getHeader().getSender())
                .receiver(obj.getHeader().getReceiver())
                .sequenceNo((short) (obj.getHeader().getSequenceNo() + 1))
                .build();
        String s = header + convert(obj);
        if (s.length() > maxTelegramLength) {
            throw new MessageMismatchException(format("Defined telegram length exceeds configured size of owms.driver.osip.telegram-length=[%d]. Actual length is [%d]", maxTelegramLength, s.length()));
        }
        return padRight(s, maxTelegramLength, getDriver().getOsip().getTelegramFiller());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String convert(ResponseMessage message) {
        return getMessageIdentifier() +
                ParserUtils.nullableBarcode(message.getBarcode()) +
                ParserUtils.nullableLocation(message.getActualLocation()) +
                ParserUtils.nullableLocation(message.getTargetLocation()) +
                ParserUtils.nullableLocationGroup(message.getTargetLocationGroup()) +
                message.getErrorCode() +
                new SimpleDateFormat(getDriver().getOsip().getDatePattern()).format(message.getCreated());
    }
}
