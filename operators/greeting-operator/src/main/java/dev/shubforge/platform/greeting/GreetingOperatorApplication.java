package dev.shubforge.platform.greeting;

import io.javaoperatorsdk.operator.Operator;

public class GreetingOperatorApplication {

    public static void main(String[] args) {

        Operator operator = new Operator();

        operator.register(
            new GreetingReconciler()
        );

        operator.installShutdownHook();
        operator.start();
    }
}
