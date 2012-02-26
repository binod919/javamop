/// TODO : consider symbols that don't appear in the SRS?

package logicrepository.plugins.srs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Set;

//This uses a slightly modified Aho-Corasick automaton
public class PatternMatchAutomaton extends LinkedHashMap<State, HashMap<Symbol, ActionState>> {
  private State s0 = new State(0); 
  private ArrayList<Set<State>> depthMap = new ArrayList<Set<State>>();
  private HashMap<State, State> fail;
  private int longestLhsSize;

  public PatternMatchAutomaton(SRS srs){
    longestLhsSize = srs.getLongestLhsSize();
    mkGotoMachine(srs);
    addFailureTransitions(srs.getTerminals());
  }

  private void mkGotoMachine(SRS srs){
    State currentState;
    put(s0, new HashMap<Symbol, ActionState>()); 
    Set<State> depthStates = new HashSet<State>();
    depthStates.add(s0);
    depthMap.add(depthStates);
    //compute one path through the tree for each lhs
    for(Rule r : srs){
      currentState = s0;
      Sequence pattern = r.getLhs();
      int patternRemaining = pattern.size() - 1;
      for(Symbol s : pattern){
        HashMap<Symbol, ActionState> transition = get(currentState);
        ActionState nextActionState = transition.get(s);  
        State nextState;
        if(nextActionState == null){
          int nextDepth = currentState.getDepth() + 1;
          nextState = 
            new State(nextDepth); 
          nextActionState = new ActionState(0, nextState);
          transition.put(s, nextActionState);
          put(nextState, new HashMap<Symbol, ActionState>());
          if(nextDepth == depthMap.size()){
            depthStates = new HashSet<State>();
            depthMap.add(depthStates);
          }
          else{
            depthStates = depthMap.get(nextDepth);
          }
          depthStates.add(nextState);
        }
        else{
          nextState = nextActionState.getState();
        }
        if(patternRemaining == 0){
          nextState.setMatch(r);
        }
        currentState = nextState;
        --patternRemaining;
      }
    }
    //now add self transitions on s0 for any symbols that don't
    //exit from s0 already
    HashMap<Symbol, ActionState> s0transition = get(s0);
    for(Symbol s : srs.getTerminals()){
      if(!s0transition.containsKey(s)){
        s0transition.put(s, new ActionState(0, s0));
      }
    }
  }

  private void addFailureTransitions(Set<Symbol> terminals){
    fail = new HashMap<State, State>();
    if(depthMap.size() == 1) return;
    //handle all depth 1
    for(State state : depthMap.get(1)){
      HashMap<Symbol, ActionState> transition = get(state);
      fail.put(state, s0);
      for(Symbol symbol : terminals){
        if(!transition.containsKey(symbol)){
          transition.put(symbol, new ActionState(1, s0));
        } 
      } 
    }
    if(depthMap.size() == 2) return;
    //handle depth d > 1
    for(int i = 2; i < depthMap.size(); ++i){
      for(State state : depthMap.get(i)){
        HashMap<Symbol, ActionState> transition = get(state);
        for(Symbol symbol : terminals){
          if(!transition.containsKey(symbol)){
            State failState = findState(state, depthMap.get(i - 1), fail, 
                                        terminals);
            transition.put(symbol,  
                new ActionState(state.getDepth() - failState.getDepth(), 
                                failState));
            fail.put(state, failState);
          } 
        } 
      }
    }

    //System.out.println("!!!!!!!!!!");
    //System.out.println(fail);
    //System.out.println("!!!!!!!!!!");
  }

  private State findState(State state, Set<State> shallowerStates, 
                          HashMap<State, State> fail, Set<Symbol> terminals){
    for(State shallowerState : shallowerStates){
      HashMap<Symbol, ActionState> transition = get(shallowerState); 
      for(Symbol symbol : terminals){
        ActionState destination = transition.get(symbol);
        if(destination.getState() == state){

      //  System.out.println(state + " " + destination.getState());
          State failState = fail.get(shallowerState);
          while(failState != s0 && get(failState).get(symbol).getAction() != 0){
            failState = fail.get(failState);
          } 
          return get(failState).get(symbol).getState();
        }
      }
    }
    return s0;
  }

  public void rewrite(SinglyLinkedList<Symbol> l){
    System.out.println("rewriting:");
    System.out.println("   " + l + "\n=========================================");
    if(l.size() == 0) return;
    Iterator<Symbol> first  = l.iterator();
    first.next(); //make sure first points to an element
    Iterator<Symbol> second = l.iterator();
    Iterator<Symbol> lastRepl;
    State currentState = s0;
    ActionState as;
    Symbol symbol = second.next();
    while(true){
      as = get(currentState).get(symbol);
      System.out.println("*" + symbol + " -- " + as);
      //adjust the first pointer
      if(currentState == s0 && as.getState() == s0){
        System.out.println("false 0 transition");
        if(!first.hasNext()) break;
        first.next();
      }
      else {
        for(int i = 0; i < as.getAction(); ++i){
          first.next();
        }
      }
      if(as.getState().getMatch() != null){
        AbstractSequence repl = as.getState().getMatch().getRhs();
        if(repl instanceof Fail){
          System.out.println("Fail!");
          return;
        }
        if(repl instanceof Succeed){
          System.out.println("Succeed!");
          return;
        }
        if(repl instanceof Sequence){
          System.out.println("==========Replacing==============" + first);
          System.out.println("==========Replacing==============" + second);
          System.out.println("in: " + l);
          l.nonDestructiveReplace(first, second, (Sequence) repl);
          if(l.isEmpty()) break;
          first = l.iterator();
          System.out.println("out: " + l);
          System.out.println("out: " + first);
          //lastRepl = l.iterator(second);
          //System.out.println("lastRepl: " + lastRepl);
          symbol = first.next();
          second = l.iterator(first);
          //System.out.println("first: " + first);
          //System.out.println("second: " + second);
          currentState = s0;
          continue;
        }
      }
      if(!second.hasNext()) break;
      currentState = as.getState();
      //normal transition
      if(as.getAction() == 0){
        symbol = second.next();
      }
      //fail transition, need to reconsider he same symbol in next state
    }
    System.out.println("substituted form = " + l.toString());
  }

  public void rewrite(SpliceList<Symbol> l){
    System.out.println("rewriting:");
    System.out.println("   " + l + "\n=========================================");
    if(l.isEmpty()) return;
    SLIterator<Symbol> first;
    SLIterator<Symbol> second;
    SLIterator<Symbol> lastRepl = null;
    State currentState = s0;
    ActionState as;
    Symbol symbol; 
    boolean changed;
DONE:
    do {
    changed = false;
    first = l.head();
    second = l.head();
    symbol = second.get();
    System.out.println("******************outer");
    if(second.equals(lastRepl)){
       System.out.println("second == lastRepl");
       break DONE;
    }
    while(true){
      as = get(currentState).get(symbol);
      System.out.println("*" + symbol + " -- " + as);
      //adjust the first pointer
      if(currentState == s0 && as.getState() == s0){
        System.out.println("false 0 transition");
        if(!first.next()) break;
      }
      else {
        for(int i = 0; i < as.getAction(); ++i){
          first.next();
        }
      }
      if(as.getState().getMatch() != null){
        AbstractSequence repl = as.getState().getMatch().getRhs();
        if(repl instanceof Fail){
          System.out.println("Fail!");
          return;
        }
        if(repl instanceof Succeed){
          System.out.println("Succeed!");
          return;
        }
        if(repl instanceof Sequence){
          changed = true;
          System.out.println("==========Replacing==============" + first);
          System.out.println("==========Replacing==============" + second);
          System.out.println("in: " + l);
          first.nonDestructiveSplice(second, (Sequence) repl);
          if(l.isEmpty()) break DONE;
          System.out.println("out: " + l);
          System.out.println("out: " + first);
          lastRepl = second;
          System.out.println("lastRepl: " + lastRepl);
          second = first.copy();
          System.out.println("first: " + first);
          System.out.println("second: " + second);
          currentState = s0;
          continue;
        }
      }
      currentState = as.getState();
      //normal transition
      if(as.getAction() == 0){
        if(!second.next()) break;
        symbol = second.get();
      }
      //fail transition, need to reconsider he same symbol in next state
    }
    } while(changed);
    System.out.println("substituted form = " + l.toString());
  }

  @Override public String toString(){
    StringBuilder sb = new StringBuilder();
    for(State state : keySet()){
      sb.append(state);
      sb.append("\n[");
      HashMap<Symbol, ActionState> transition = get(state);
      for(Symbol symbol : transition.keySet()){
        sb.append("  ");
        sb.append(symbol);
        sb.append(" -> ");
        sb.append(transition.get(symbol));
        sb.append("\n");
      }
      sb.append("]\n");
    }
    return sb.toString();
  }
}

class ActionState {
  private int action;
  private State state;

  public int getAction(){
    return action;
  }

  public State getState(){
    return state;
  }

  public ActionState(int action, State state){
    this.action = action;
    this.state = state;
  }

  @Override public String toString(){
    return "[" + action + "] " + state.toString();
  }
}

class State {
  private static int counter = 0;
  private int number;
  private int depth;
  private Rule matchedRule = null;

  public State(int depth){
    number = counter++;
    this.depth = depth;
  }

  public int getNumber(){
    return number;
  }

  public int getDepth(){
    return depth;
  }

  public Rule getMatch(){
    return matchedRule;
  }

  public void setMatch(Rule r){
    matchedRule = r;
  }

  @Override
  public String toString(){
      return "<" + number 
                 + " @ " 
                 + depth 
                 + ((matchedRule == null)?
                     ""
                   : " matches " + matchedRule.toString()
                   ) 
              + ">";
  }
}
