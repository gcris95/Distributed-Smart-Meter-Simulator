package simulation;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SmartMeterSimulator extends Simulator {

    private static final double A = 0.4;
    private static final double W = 0.01;
    private static final long BOOST_DURATION_MS = 5000;
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

    private volatile boolean boost = false;

    public SmartMeterSimulator(Buffer buffer) {
        super("plug-" + ID_GENERATOR.getAndIncrement(), "Plug", buffer);
    }

    @Override
    public void run() {
        double i = rnd.nextDouble() * 2 * Math.PI;

        long waitingTime;

        while(!stopCondition){

            double value = getElectricityValue(i);

            if(boost)
                value+=3.0;

            addMeasurement(value);

            waitingTime = 100 + rnd.nextInt(200);
            sensorSleep(waitingTime);

            i+=0.2;
        }
    }

    private double getElectricityValue(double t){
        return Math.abs(A * Math.sin(W * t) + rnd.nextGaussian() * 0.3);
    }

    public void boost(){
        boost = true;

        try {
            // Trattiene l'esecuzione per 5 secondi esatti
            Thread.sleep(BOOST_DURATION_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            this.boost = false; // Spegne il boost alla fine del tempo
        }
    }
}
