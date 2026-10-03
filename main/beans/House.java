package beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="house")
@XmlAccessorType(XmlAccessType.FIELD)
public class House{
    @XmlElement(name = "id")
    private int id;
    @XmlElement (name = "ip")
    private String ipAddress;
    @XmlElement (name = "port")
    private int port;
    @XmlElement (name = "timestamp")
    private long timestamp;

    public House(){}

    public House(int id, String ipAddress, int port){
        this.id = id;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public String getIpAddress() { return ipAddress; }

    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
