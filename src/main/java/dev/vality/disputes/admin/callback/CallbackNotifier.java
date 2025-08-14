package dev.vality.disputes.admin.callback;

import dev.vality.disputes.domain.tables.pojos.Dispute;

public interface CallbackNotifier {

    void notify(Dispute dispute);

}
