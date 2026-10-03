package beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="consume")
@XmlAccessorType(XmlAccessType.FIELD)
public class Consume {
    @XmlElement (name = "id")
    private int houseId;
    @XmlElement (name = "consume")
    private double consume;
    @XmlElement (name = "timestamp")
    private long timestamp;

    public Consume() {
    }

    public Consume(int houseId, double consume, long timestamp) {
        this.houseId = houseId;
        this.consume = consume;
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getHouseId() {
        return houseId;
    }

    public void setHouseId(int house_id) {
        this.houseId = house_id;
    }

    public double getConsume() {
        return consume;
    }

    public void setConsume(double consume) {
        this.consume = consume;
    }

}
