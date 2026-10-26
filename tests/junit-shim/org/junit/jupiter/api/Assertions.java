package org.junit.jupiter.api;
public final class Assertions {
  public static void assertEquals(Object e, Object a) { if (!java.util.Objects.equals(e, a)) throw new AssertionError("expected <" + e + "> but was <" + a + ">"); }
  public static void assertTrue(boolean c) { if (!c) throw new AssertionError("expected true"); }
  public static void assertTrue(boolean c, String m) { if (!c) throw new AssertionError(m); }
  public static void assertFalse(boolean c) { if (c) throw new AssertionError("expected false"); }
  public interface Executable { void execute() throws Throwable; }
  public static <T extends Throwable> T assertThrows(Class<T> type, Executable ex) {
    try { ex.execute(); } catch (Throwable t) {
      if (type.isInstance(t)) return type.cast(t);
      throw new AssertionError("expected " + type.getName() + " but got " + t);
    }
    throw new AssertionError("expected " + type.getName() + " but nothing was thrown");
  }
}
