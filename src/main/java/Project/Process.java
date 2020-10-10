package Project;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import Project.*;

public class Process extends UntypedAbstractActor {

    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor
    private final int N;//number of processes
    private final int id;//id of current process
    private Members processes;//other processes' references
    private Integer proposal;
    private int value;
    private int timestamp;
    private int majority;
    private int answers; //number of answers after a request
    private int state; //1 if the process is faulty, 0 otherwise
    private ArrayList <Integer> readValues; //save the values read 
    private ArrayList <Integer> readTimestamp; //save the read timestamp
    private int M; //number of operations
    private int done; //number of operations already performed 
    
    public Process(int ID, int nb, int M) {
        N = nb;
        id = ID;
        majority = N/2;
        answers = 0;
        state = 0;
        timestamp = 0;
        this.M = M;
        done = 0;
    }
    
    public String toString() {
        return "Process{" + "id=" + id ;
    }

    /**
     * Static function creating actor
     */
    public static Props createActor(int ID, int nb, int M) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb, M);
        });
    }
    
    
    public void onReceive(Object message) throws Throwable {
        if (message instanceof Members && state == 0) {//save the system's info
            Members m = (Members) message;
            processes = m;
            log.info("p" + self().path().name() + " received processes info");
            this.nextOperation();
        }
        else if (message instanceof WriteMsg && state == 0) {
            WriteMsg m = (WriteMsg) message;
            this.writeReceived(m, getSender());
        }
        else if (message instanceof ReadMsg && state == 0) {
            ReadMsg m = (ReadMsg) message;
            this.readReceived(m, getSender());
        }
        else if (message instanceof ReceivedWrite && state == 0){
            if (message.timestamp == this.timestamp){
                answers++;
                if (answers > majority - 1){
                    answers = 0;
                    log.info("A majority of processes acknowledged write operation with value "+message.value+" at timestamp "+message.timestamp+" by "+self().path().name());
                    this.done++;
                    this.nextOperation();
                }
            }
        }
        else if (message instanceof ReceivedRead && state == 0){
            if (message.readAnswer.get("timestamp") >= this.timestamp){
                answers++;
                readValues.add(message.readAnswer.get("value"));
                readTimestamp.add(message.readAnswer.get("timestamp"));
                if (answers > majority - 1){
                    proposal = Collections.max(readTimestamp);
                    if (timestamp < proposal){
                        timestamp = proposal;
                        if (message.overrideValue){
                            value = readValues.get(readTimestamp.indexOf(proposal));
                        }
                    }
                    if (message.overrideValue){
                        put(this.value, false);
                    }
                    answers = 0;
                    log.info("A majority of processes answered read operation from "+self().path().name()+"THe new value is "+this.value+" and new timestamp is  "+this.timestamp);
                    this.done++;
                    this.nextOperation();
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

        ReceivedRead confirmation = new ReceivedRead(readAnswer, message.overrideValue);

        sender.tell(confirmation, getSender());

        log.info("Read operation from "+sender.path().name()+"received by process "+self().path().name());
    }

    public void put(int value, boolean getBefore){

        log.info("Write operation launch by process "+self().path().name()+" with the value "+value+" at timestamp "+timestamp);

        if (getBefore){
            get(false);
        }

        timestamp++;
        answers = 0;
        this.value = value;

        WriteMsg message = new WriteMsg(value, timestamp);

        for (ActorRef actor : processes.references) {
            actor.tell(message, this.getSelf());
        }
    }

    public void get(boolean overrideValue){

        readValues = new ArrayList<Integer>();
        readTimestamp = new ArrayList<Integer>();

        readValues.add(this.value);
        readTimestamp.add(this.timestamp);
        answers = 0;

        ReadMsg message = new ReadMsg(overrideValue);

        for (ActorRef actor : processes.references) {
            actor.tell(message, this.getSelf());
        }

        log.info("Read operation launch by process "+self().path().name());
    }

    private void nextOperation(){
        
        if (this.done < this.M){
            this.put(this.done * this.processes.references.size() + this.id, true);
        }
        else if (this.done < 2 * this.M){
            this.get(true);
        }
        else {
            log.info("Process "+self().path().name()+" has finished his operations");
        }
    }
}
