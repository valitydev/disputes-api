package dev.vality.disputes.dao.model;

import dev.vality.disputes.domain.tables.pojos.Dispute;
import dev.vality.disputes.domain.tables.pojos.Notification;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class EnrichedNotification {

    private Notification notification;
    private Dispute dispute;

}
