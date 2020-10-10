package Project;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.lang.Math;

public class Process extends UntypedAbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor
    private final int N;//number of processes
    private final int id;//id of current process
    private Members processes;//other processes' references
    private int proposedTimestamp; //save the proposed timestamp in case it changes, so we can still receive ackowledgements
    private int proposedValue; //save the value which will be written before reading 
    private int value; //current value stored
    private int timestamp; //current timestamp
    private int majority; 
    private int answers; //number of answers after a request
    private int state; //1 if the process is active, 0 is the process is faulty
    private ArrayList <Integer> readValues; //save the values read 
    private ArrayList <Integer> readTimestamp; //save the read timestamp
    private int M; //number of operations
    private int done; //number of operations already performed 
    private int currentRead; //save the current read number to be sure to only handle answers corresponding to the last read 
    
    public Process(int ID, int nb, int M, int state) {
        this.N = nb;
        this.id = ID;
        this.majority = (int) Math.ceil(N / 2);
        this.answers = 0;
        this.state = state;
        this.timestamp = 0;
        this.M = M;
        this.done = 0;
        this.value = 0;
        this.currentRead = 0;

        if (state == 0){
            log.info("Process "+self().path().name()+" is faulty");
        }
        else {
            log.info("Process "+self().path().name()+" is active");
        }
    }
    
    public String toString() {
        return "Process{" + "id=" + id ;
    }

    /**
     * Static function creating actor
     */
    public static Props createActor(int ID, int nb, int M, int state) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb, M, state);
        });
    }
    
    
    public void onReceive(Object message) throws Throwable {
        if (message instanceof Members && state == 1) {//save the system's info
            Members m = (Members) message;
            processes = m;
            log.info("p" + self().path().name() + " received processes info");
            this.nextOperation();
        }
        else if (message instanceof WriteMsg && state == 1) {
            WriteMsg m = (WriteMsg) message;
            this.writeReceived(m, getSender());
        }
        else if (message instanceof ReadMsg && state == 1) {
            ReadMsg m = (ReadMsg) message;
            this.readReceived(m, getSender());
        }
        else if (message instanceof ReceivedWrite && state == 1){
            ReceivedWrite m = (ReceivedWrite) message;
            this.acknowledgementHandling(m);
        }
        else if (message instanceof ReceivedRead && state == 1){
            ReceivedRead m = (ReceivedRead) message;
            this.readAnswerHandling(m);
        }
    }

    private void acknowledgementHandling(ReceivedWrite m){
        if (m.timestamp == this.proposedTimestamp){
            answers++;
            if (answers >= majority - 1){
                answers = 0;
                log.info("A majority of processes acknowledged write operation by process "+self().path().name()+". My current value is "+this.value+" and my current timestamp is "+this.timestamp);
                this.done++;
                this.nextOperation();
            }
        }
    }

    private void readAnswerHandling(ReceivedRead m){
        if (this.currentRead == m.currentRead){
            answers++;
            readValues.add(m.readAnswer.get("value"));
            readTimestamp.add(m.readAnswer.get("timestamp"));

            if (answers >= majority - 1){
                int maxTimestamp = Collections.max(readTimestamp);
                if (timestamp < maxTimestamp){
                    timestamp = maxTimestamp;
                    int minimum = readValues.get(0);
                    for (int i =1; i < readValues.size(); i++){
                        if (readTimestamp.get(i) == maxTimestamp && readValues.get(i) < minimum){
                            minimum = readValues.get(i);
                        }
                    }
                    this.value = minimum;
                }
                else if (timestamp == maxTimestamp){
                    int minimum = readValues.get(0);
                    for (int i =1; i < readValues.size(); i++){
                        if (readTimestamp.get(i) == maxTimestamp && readValues.get(i) < minimum){
                            minimum = readValues.get(i);
                        }
                    }
                    this.value = minimum;
                }
                answers = 0;
                log.info("A majority of processes answered read operation from "+self().path().name()+". My current value is "+this.value+" and my current timestamp is "+this.timestamp);
                if (m.putAfter){
                    invokePut(this.value, false);
                }
                else {
                    put();
                }
            }
        }
    }

    private void writeReceived(WriteMsg message, ActorRef sender){

        int proposedValue = message.value;
        int proposedTimestamp = message.timestamp;

        if (proposedTimestamp > this.timestamp){
            this.value = proposedValue;
            this.timestamp = proposedTimestamp;
        }
        else if (proposedTimestamp == this.timestamp && proposedValue < this.value){
            this.value = proposedValue;
        }

        ReceivedWrite confirmation = new ReceivedWrite(proposedTimestamp);

        sender.tell(confirmation, getSender());

        log.info("Write operation with value "+message.value+" at timestamp "+message.timestamp+" acknowledged by process "+self().path().name());
    }

    private void readReceived(ReadMsg message, ActorRef sender){

        HashMap<String, Integer> readAnswer = new HashMap<String, Integer>();

        readAnswer.put("value", this.value);
        readAnswer.put("timestamp", this.timestamp);

        ReceivedRead confirmation = new ReceivedRead(readAnswer, message.putAfter, message.currentRead);

        sender.tell(confirmation, getSender());

        log.info("Read operation from process "+sender.path().name()+" received by process "+self().path().name());
    }

    public void invokePut(int value, boolean getBefore){

        this.proposedValue = value;

        log.info("Write operation launch by process "+self().path().name()+" with the value "+value);

        if (getBefore){
            get(false);
        }
        else {
            put();
        }
    }

    public void put(){

        this.value = this.proposedValue;
        this.timestamp++;
        this.proposedTimestamp = this.timestamp;
        this.answers = 0;

        WriteMsg message = new WriteMsg(this.value, this.timestamp);

        for (ActorRef actor : processes.references) {
            if (actor.path().name() != self().path().name()){
                actor.tell(message, this.getSelf());
            }
        }
    }

    public void get(boolean putAfter){

        readValues = new ArrayList<Integer>();
        readTimestamp = new ArrayList<Integer>();

        readValues.add(this.value);
        readTimestamp.add(this.timestamp);
        answers = 0;
        currentRead++;

        ReadMsg message = new ReadMsg(putAfter, currentRead);

        for (ActorRef actor : processes.references) {
            if (actor.path().name() != self().path().name()){
                actor.tell(message, this.getSelf());
            }
        }

        log.info("Read operation launch by process "+self().path().name());
    }

    private void nextOperation(){
        if (this.done < this.M){
            this.invokePut(this.done * this.N + Integer.parseInt(self().path().name()), true);
        }
        else if (this.done < 2 * this.M){
            this.get(true);
        }
        else {
            log.info("Process "+self().path().name()+" has finished his operations");
            this.sumUp();
        }
    }

    private void sumUp(){
        
        log.info("Process "+self().path().name()+" [ current value : "+this.value+" | current timestamp : "+this.timestamp+" | performed operations : "+this.done+" | requested operations : "+2 * this.M+" ]");
    }
}
