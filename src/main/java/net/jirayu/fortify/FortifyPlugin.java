package net.jirayu.fortify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FortifyPlugin {
    private static final Logger log = LoggerFactory.getLogger(FortifyPlugin.class);

    public FortifyPlugin() {
        log.info("Fortify plugin loaded");
    }
}
