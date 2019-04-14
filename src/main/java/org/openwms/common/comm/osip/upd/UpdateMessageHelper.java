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
package org.openwms.common.comm.osip.upd;

import org.openwms.common.comm.osip.OSIPHeader;
import org.springframework.messaging.support.GenericMessage;

/**
 * A UpdateMessageHelper.
 *
 * @author <a href="mailto:hscherrer@interface21.io">Heiko Scherrer</a>
 */
final class UpdateMessageHelper {

    private UpdateMessageHelper() {
    }

    static UpdateVO getRequest(GenericMessage<UpdateMessage> msg) {
        return new UpdateVO.Builder()
                .type("UPD_")
                .barcode(msg.getPayload().getBarcode())
                .actualLocation(msg.getPayload().getActualLocation())
                .errorCode(msg.getPayload().getErrorCode())
                .created(msg.getPayload().getCreated())
                .header(new UpdateVO.UpdateHeaderVO.Builder()
                        .receiver(msg.getHeaders().get(OSIPHeader.RECEIVER_FIELD_NAME, String.class))
                        .sender(msg.getHeaders().get(OSIPHeader.SENDER_FIELD_NAME, String.class))
                        .sequenceNo(""+msg.getHeaders().get(OSIPHeader.SEQUENCE_FIELD_NAME, Short.class))
                        .build())
                .build();
    }

    static UpdateVO getXRequest(GenericMessage<UpdateXMessage> msg) {
        return new UpdateVO.Builder()
                .type("UPDX")
                .barcode(msg.getPayload().getBarcode())
                .actualLocation(msg.getPayload().getActualLocation())
                .errorCode(msg.getPayload().getErrorCode())
                .created(msg.getPayload().getCreated())
                .header(new UpdateVO.UpdateHeaderVO.Builder()
                        .receiver(msg.getHeaders().get(OSIPHeader.RECEIVER_FIELD_NAME, String.class))
                        .sender(msg.getHeaders().get(OSIPHeader.SENDER_FIELD_NAME, String.class))
                        .sequenceNo(""+msg.getHeaders().get(OSIPHeader.SEQUENCE_FIELD_NAME, Short.class))
                        .build())
                .build();
    }
}
