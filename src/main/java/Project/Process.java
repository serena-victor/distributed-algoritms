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
    private int state; //1 if the process is waiting for answers, 0 otherwise
    private ArrayList <Integer> readValues; //save the values read 
    private ArrayList <Integer> readTimestamp; //save the read timestamp
    
    public Process(int ID, int nb) {
        N = nb;
        id = ID;
        majority = N/2;
        answers = 0;
        state = 0;
        timestamp = 0;
    }
    
    public String toString() {
        return "Process{" + "id=" + id ;
    }

    /**
     * Static function creating actor
     */
    public static Props createActor(int ID, int nb) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb);
        });
    }
    
    
    public void onReceive(Object message) throws Throwable {
        if (message instanceof Members) {//save the system's info
            Members m = (Members) message;
            processes = m;
            log.info("p" + self().path().name() + " received processes info");
        }
        else if (message instanceof WriteMsg) {
            WriteMsg m = (WriteMsg) message;
            this.writeReceived(m, getSender());
        }
        else if (message instanceof ReadMsg) {
            ReadMsg m = (ReadMsg) message;
            this.readReceived(m, getSender(), m.overrideValue);
        }
        else if (message instanceof receivedWrite){
            if (state == 1 && message.timestamp == this.timestamp){
                answers++;
                if (answers > majority - 1){
                    answers = 0;
                    state = 0;
                    log.info("A majority of processes acknowledged write operation with value "+message.value+" at timestamp "+message.timestamp+" by "+self().path().name());
                }
            }
        }
        else if (message instanceof receivedRead){
            if (state == 1 && message.timestamp >= this.timestamp){
                answers++;
                readValues.add(message.getValue());
                readTimestamp.add(message.getTimestamp());
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
                    state = 0;
                    answers = 0;
                    log.info("A majority of processes answered read operation from "+message.sender.path().name()+"THe new value is "+this.value+" and new timestamp is  "this.timestamp);
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

        receivedWrite confirmation = new receivedWrite(proposedTimestamp);

        sender.tell(confirmation);

        log.info("Write operation with value "+message.value+" at timestamp "+message.timestamp+" acknowledged by process "+self().path().name());
    }

    private void readReceived(ReadMsg message, ActorRef sender, boolean overrideValue){

        HashMap<String, Integer> readAnswer = new HashMap<String, Integer>();

        readAnswer.put("value", this.value);
        readAnswer.put("timestamp", this.timestamp);

        receivedRead confirmation = new receivedRead(readAnswer, overrideValue);

        sender.tell(confirmation);

        log.info("Read operation from "+sender.path().name()+"received by process "+self().path().name());
    }

    public void put(int value, boolean getBefore){

        log.info("Write operation launch by process "+self().path().name()+" with the value "+value+" at timestamp "+timestamp);

        if (getBefore){
            get(false);
        }

        timestamp++;
        answers = 0;
        state = 1;
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
        state = 1;

        ReadMsg message = new ReadMsg(overrideValue);

        for (ActorRef actor : processes.references) {
            actor.tell(message, this.getSelf());
        }

        log.info("Read operation launch by process "+self().path().name());
    }
}
