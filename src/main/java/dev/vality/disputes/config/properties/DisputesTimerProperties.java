package dev.vality.disputes.config.properties;

import dev.vality.adapter.flow.lib.utils.TimerProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties("time.config")
public class DisputesTimerProperties extends TimerProperties {

}
