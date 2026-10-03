package dev.shubforge.platform.application;

import io.javaoperatorsdk.operator.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlatformOperatorApplication {

    private static final Logger log =
        LoggerFactory.getLogger(
            PlatformOperatorApplication.class
        );

    private PlatformOperatorApplication() {
    }

    public static void main(String[] args) {

        log.info("Starting Platform Operator");

        var operator =
            new Operator();

        operator.register(
            new ApplicationReconciler()
        );

        Runtime.getRuntime()
            .addShutdownHook(
                new Thread(operator::stop)
            );

        operator.start();
    }
}
