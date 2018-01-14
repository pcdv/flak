package net.jflask.test.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * Allows to memorize the state of active threads so that we can compare it
 * with the new state later on.
 *
 * @author pcdv
 */
public class ThreadState {

  public static boolean ENABLE_CHECK =
      !System.getProperty("checkNewThreads", "true").equalsIgnoreCase("false");

  private Thread[] threads;

  private final Set<Pattern> ignoredThreads = new HashSet<>();

  public ThreadState() {
    this(true);
  }

  public ThreadState(boolean refresh) {
    if (refresh)
      refreshThreads();
  }

  private void refreshThreads() {
    threads = new Thread[Thread.activeCount()];
    // NB: race: some of the threads may have died in the mean time
    Thread.enumerate(threads);
  }

  Map<String, Thread> toMap() {
    HashMap<String, Thread> m = new HashMap<>();
    for (Thread t : threads) {
      if (t != null) // 'threads' array can contain null values (race condition)
        m.put(t.getName(), t);
    }
    return m;
  }

  public ThreadState ignore(final Set<String> ignoredThreads) {
    for (String s : ignoredThreads) {
      ignore(s);
    }
    return this;
  }

  public ThreadState ignore(String... threadPatterns) {
    for (String n : threadPatterns) {
      ignoredThreads.add(Pattern.compile(n));
    }
    return this;
  }

  public String diff(ThreadState other) {
    return diff(other, false);
  }

  private String stack(Thread th) {
    StringBuilder s = new StringBuilder(2000);
    logStacks(s, th, false);
    return s.toString();
  }

  /**
   * Logs the stacks of all active threads into specified appender, starting
   * with specified main thread (which may be null).
   *
   * @param s a string builder in which strings will be appended
   * @param main an optional main thread that must be printed in 1st position
   * @param allStacks if true, will log all active threads' stack (otherwise,
   * only main thread's stack)
   */
  public static void logStacks(StringBuilder s,
                               Thread main,
                               boolean allStacks) {
    if (main != null) {
      s.append(main.getName());
      appendStack(main, s);
      if (allStacks)
        s.append("\n\nOther threads:");
    }

    if (allStacks) {
      Map<Thread, StackTraceElement[]> v = Thread.getAllStackTraces();
      for (Thread t : v.keySet()) {
        if (t != main) {
          s.append("\n\n").append(t.getName()).append(':');
          appendStack(t, s);
        }
      }
    }
  }

  private static void appendStack(Thread th, StringBuilder s) {
    StackTraceElement[] st = th.getStackTrace();
    final int len = st.length;
    if (len == 0)
      s.append("\n    no stack, state ").append(th.getState().name());

    else {
      for (int i = 0; i < st.length; i++) {
        s.append("\n    at ");
        s.append(st[i]);
      }
    }
  }

  public String diff(ThreadState other, boolean checkRemovedThreads) {
    Map<String, Thread> previous = toMap();
    Map<String, Thread> actual = other.toMap();

    ArrayList<String> added = new ArrayList<>();
    ArrayList<String> removed = new ArrayList<>();

    for (String name : previous.keySet())
      if (!actual.containsKey(name) && !isIgnored(name))
        removed.add(name);

    for (String name : actual.keySet()) {
      Thread thread = actual.get(name);
      if (!previous.containsKey(name) && !isIgnored(name) && thread.isAlive()) {
        added.add(name);
        System.err.println(stack(thread));
      }
    }

    String res = "";
    if (!added.isEmpty())
      res = res + "+" + added;
    if (checkRemovedThreads && !removed.isEmpty())
      res = res + "-" + removed;
    return res;
  }

  private boolean isIgnored(String name) {
    for (Pattern pat : ignoredThreads) {
      if (pat.matcher(name).matches())
        return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(threads);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ThreadState &&
           Arrays.equals(threads, ((ThreadState) obj).threads);
  }

  public void assertNoChange() throws InterruptedException {
    ThreadState newState = new ThreadState();
    if (!newState.equals(this)) {
      Thread.sleep(5);
      newState = new ThreadState();
      String diff = diff(newState);
      if (diff.length() > 0) {
        refreshThreads();
        System.err.println("Active threads: " + Arrays.toString(threads));
        throw new IllegalStateException("Thread state has changed: " + diff +
                                        ". Use -DcheckNewThreads=false to disable check.");
      }
    }
  }

  public static final class ThreadStateRule extends TestWatcher {

    private final ThreadState state;

    private boolean disabled;

    public ThreadStateRule() {
      this.state = new ThreadState(false);
      this.disabled = !ENABLE_CHECK;
    }

    public ThreadStateRule(String... ignored) {
      this();
      ignore(ignored);
    }

    public void ignore(Set<String> ignored) {
      this.state.ignore(ignored);
    }

    public void ignore(String... patterns) {
      this.state.ignore(patterns);
    }

    public void disable() {
      this.disabled = true;
    }

    @Override
    public void starting(Description desc) {
      this.state.refreshThreads();
    }

    @Override
    public void succeeded(Description desc) {
      if (!disabled)
        try {
          this.state.assertNoChange();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
    }
  }
}
