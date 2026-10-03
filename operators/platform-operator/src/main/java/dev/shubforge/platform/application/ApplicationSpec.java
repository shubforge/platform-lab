package dev.shubforge.platform.application;

public class ApplicationSpec {

    private String image;

    private Integer replicas = 1;

    private ApplicationPort port;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Integer getReplicas() {
        return replicas;
    }

    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }

    public ApplicationPort getPort() {
        return port;
    }

    public void setPort(ApplicationPort port) {
        this.port = port;
    }
}
