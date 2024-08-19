package dev.vality.disputes.service.external;

import dev.vality.bouncer.decisions.Resolution;
import dev.vality.disputes.security.AccessData;


public interface BouncerService {

    Resolution getResolution(AccessData accessData);

}
