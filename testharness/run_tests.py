#!/usr/bin/env python3
"""Runs the offline-owner test matrix on a real Forge dedicated server.

Usage: run_tests.py <server_dir> <projectcell_jar> [--baseline]

<server_dir> must contain a Forge 1.20.1-47.4.20 server (run.sh) and mods/ with AE2, GuideME,
ProjectE, Team ProjectE and Project Expansion. The script copies <projectcell_jar> and the
pctest harness into mods/, wipes the world, and runs three server sessions with restarts.
With --baseline it only runs the offline check, to show how the original jar behaves.
"""
import os
import queue
import re
import shutil
import subprocess
import sys
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
N1 = "0 -60 0"   # drive: Alice's cells (+ Bob's for the shared-network case)
N2 = "0 -60 4"   # drive: Bob's cell and a cell bound to a never-seen player
FLOWER = "4 -60 8"
DIAMOND, IRON, COBBLE = "minecraft:diamond", "minecraft:iron_ingot", "minecraft:cobblestone"
ENCHANTED = 'minecraft:diamond_sword{Enchantments:[{id:"minecraft:sharpness",lvl:1s}]}'
DAMAGED = "minecraft:diamond_sword{Damage:5}"
CAP = 2147483647  # limitItemCount (default true) caps each listed amount

results = []


def check(label, ok, detail=""):
    results.append((label, ok))
    print(("PASS " if ok else "FAIL ") + label + (("  [" + detail + "]") if detail else ""), flush=True)


class Server:
    def __init__(self, path):
        self.path = path
        self.lines = queue.Queue()
        SERVERS.append(self)

    def start(self):
        self.proc = subprocess.Popen(["bash", "run.sh", "nogui"], cwd=self.path, stdin=subprocess.PIPE,
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
        threading.Thread(target=self._read, daemon=True).start()
        self.wait_for(lambda l: "Done (" in l and "For help" in l, 1500)

    def _read(self):
        with open(os.path.join(self.path, "test-console.log"), "a") as log:
            for line in self.proc.stdout:
                log.write(line)
                log.flush()
                self.lines.put(line.rstrip("\n"))

    def wait_for(self, pred, timeout=60):
        end = time.time() + timeout
        while time.time() < end:
            try:
                line = self.lines.get(timeout=1)
            except queue.Empty:
                if self.proc.poll() is not None:
                    raise RuntimeError("server exited")
                continue
            if "PCTEST ERROR" in line:
                raise RuntimeError(line)
            if pred(line):
                return line
        raise TimeoutError("timed out waiting for server output")

    def send(self, cmd):
        self.proc.stdin.write(cmd + "\n")
        self.proc.stdin.flush()

    def pc(self, cmd, timeout=30):
        """Runs a pctest command and returns its PCTEST result line."""
        self.send("pctest " + cmd)
        word = cmd.split()[0]
        line = self.wait_for(lambda l: ("PCTEST " + word) in l, timeout)
        return line.split("PCTEST ", 1)[1]

    def raw(self, cmd, pause=1.5):
        self.send(cmd)
        time.sleep(pause)

    def stop(self):
        self.send("stop")
        self.proc.wait(timeout=300)

    def kill(self):
        if getattr(self, "proc", None) and self.proc.poll() is None:
            self.proc.kill()
            self.proc.wait()


def num(line, key):
    m = re.search(re.escape(key) + r"=(-?\d+)", line)
    return int(m.group(1)) if m else None


def emc(s, name):
    line = s.pc("emc " + name)
    m = re.search(r"= (-?\d+|none)", line)
    return None if m.group(1) == "none" else int(m.group(1))


def value(s, item):
    line = s.pc("value " + item)
    return int(re.search(r"= (\d+)", line).group(1)), num(line, "sell")


def knows(s, name, item):
    return s.pc("knows %s %s" % (name, item)).endswith("true")


def count(s, pos, item):
    line = s.pc("count %s %s" % (pos, item))
    return num(line, "fresh"), num(line, "cached"), num(line, "scanMicros")


def insert(s, pos, item, n):
    return num(s.pc("insert %s %s %d" % (pos, item, n)), "inserted")


def extract(s, pos, item, n):
    return num(s.pc("extract %s %s %d" % (pos, item, n)), "extracted")


def prepare(server_dir, jar):
    mods = os.path.join(server_dir, "mods")
    os.makedirs(mods, exist_ok=True)
    for f in os.listdir(mods):
        if f.startswith(("projectcell", "ProjectCell", "pctest")):
            os.remove(os.path.join(mods, f))
    shutil.copy(jar, mods)
    harness = os.path.join(HERE, "build", "libs")
    shutil.copy(os.path.join(harness, [f for f in os.listdir(harness) if f.startswith("pctest")][0]), mods)
    shutil.rmtree(os.path.join(server_dir, "world"), ignore_errors=True)
    with open(os.path.join(server_dir, "eula.txt"), "w") as f:
        f.write("eula=true\n")
    with open(os.path.join(server_dir, "server.properties"), "w") as f:
        f.write("online-mode=false\nlevel-type=minecraft\\:flat\ngenerate-structures=false\n"
                "spawn-protection=0\nview-distance=4\nsimulation-distance=4\nspawn-monsters=false\n"
                "spawn-animals=false\nmax-tick-time=-1\nserver-port=25599\n")
    with open(os.path.join(server_dir, "user_jvm_args.txt"), "w") as f:
        mem = os.environ.get("PCTEST_XMX", "2G")
        f.write("-Xms%s\n-Xmx%s\n" % (mem, mem))


def set_flower_delay(server_dir, delay):
    path = os.path.join(server_dir, "config", "project-expansion.toml")
    text = open(path).read()
    text = re.sub(r"tickDelay = \d+", "tickDelay = %d" % delay, text)
    open(path, "w").write(text)


def build_networks(s):
    s.raw("forceload add -16 -16 16 16")
    for pos in (N1, N2):
        x, y, z = pos.split()
        s.raw("setblock %s ae2:drive" % pos, 0.5)
        s.raw("setblock %d %s %s ae2:creative_energy_cell" % (int(x) + 1, y, z), 0.5)
    time.sleep(3)


def baseline(server_dir):
    s = Server(server_dir)
    s.start()
    build_networks(s)
    s.pc("login Alice")
    s.pc("setemc Alice 1000000")
    s.pc("putcell %s 0 Alice true" % N1)
    time.sleep(1)
    insert(s, N1, DIAMOND, 1)
    online = count(s, N1, DIAMOND)[0]
    s.pc("logout Alice")
    time.sleep(1)
    offline = count(s, N1, DIAMOND)[0]
    ins = insert(s, N1, COBBLE, 5)
    print("BASELINE online diamonds=%s offline diamonds=%s offline insert accepted=%s" % (online, offline, ins))
    s.stop()


def bench(server_dir):
    """Median AE2 listing time for a cell whose owner (offline) knows 1,085 or all items."""
    s = Server(server_dir)
    s.start()
    build_networks(s)
    s.pc("login Alice")
    s.pc("setemc Alice 100000000000")
    s.pc("putcell %s 0 Alice true" % N1)
    s.pc("logout Alice")
    time.sleep(1)
    for label, full in (("1533 items (full knowledge)", True),):
        s.pc("fullknowledge Alice %s" % ("true" if full else "false"))
        times = sorted(count(s, N1, COBBLE)[2] for _ in range(40))[5:35]
        print("BENCH %s: median %d us, min %d us" % (label, times[len(times) // 2], times[0]))
    s.stop()


def session_a(s):
    build_networks(s)
    v_dia, sell_dia = value(s, DIAMOND)
    v_iron, sell_iron = value(s, IRON)
    s.pc("login Alice")
    s.pc("login Bob")
    s.pc("setemc Alice 1000000")
    s.pc("setemc Bob 500000")
    s.pc("putcell %s 0 Alice true" % N1)
    time.sleep(1)

    # --- online basics
    check("unknown item not listed", count(s, N1, DIAMOND)[0] == 0)
    check("unknown item not extractable", extract(s, N1, DIAMOND, 1) == 0)
    e0 = emc(s, "Alice")
    check("online insert accepted", insert(s, N1, DIAMOND, 2) == 2)
    check("online insert adds sell value", emc(s, "Alice") == e0 + 2 * sell_dia)
    check("online insert teaches item", knows(s, "Alice", DIAMOND))
    e1 = emc(s, "Alice")
    check("online count = emc / value", count(s, N1, DIAMOND)[0] == e1 // v_dia)
    check("online extract", extract(s, N1, DIAMOND, 3) == 3)
    check("online extract costs value", emc(s, "Alice") == e1 - 3 * v_dia)
    e2 = emc(s, "Alice")
    check("NBT filter refuses enchanted item", insert(s, N1, ENCHANTED, 1) == 0)
    check("NBT filter refuses damaged item", insert(s, N1, DAMAGED, 1) == 0)
    check("refused items cost nothing", emc(s, "Alice") == e2)

    # --- owner offline, another player online
    s.pc("logout Alice")
    time.sleep(1)
    fresh, cached, _ = count(s, N1, DIAMOND)
    check("offline: contents still listed", fresh == e2 // v_dia and fresh > 0, "fresh=%s emc=%s" % (fresh, e2))
    time.sleep(1)
    check("offline: AE2 cached view matches", count(s, N1, DIAMOND)[1] == e2 // v_dia)
    check("offline: iron not known yet", not knows(s, "Alice", IRON))
    check("offline: insert accepted", insert(s, N1, IRON, 10) == 10)
    check("offline: insert adds sell value", emc(s, "Alice") == e2 + 10 * sell_iron)
    check("offline: insert teaches item", knows(s, "Alice", IRON))
    check("cache: newly learned item listed at once", count(s, N1, IRON)[0] == emc(s, "Alice") // v_iron)
    e3 = emc(s, "Alice")
    check("offline: extract", extract(s, N1, DIAMOND, 4) == 4)
    check("offline: extract costs value", emc(s, "Alice") == e3 - 4 * v_dia)
    check("offline: NBT filter still refuses", insert(s, N1, ENCHANTED, 1) == 0)

    # --- second cell, same owner: no duplication when draining
    s.pc("putcell %s 1 Alice false" % N1)
    time.sleep(1)
    e4 = emc(s, "Alice")
    affordable = e4 // v_dia
    got = extract(s, N1, DIAMOND, 10 ** 9)
    e5 = emc(s, "Alice")
    check("two cells same owner: no duplication", got == affordable and e5 == e4 - got * v_dia and e5 >= 0,
          "affordable=%d got=%d emcAfter=%d" % (affordable, got, e5))
    check("drained: nothing more extractable", extract(s, N1, DIAMOND, 1) == 0 and emc(s, "Alice") == e5)
    s.pc("takecell %s 1" % N1)

    # --- second player's cell in its own network, plus a never-seen owner
    s.pc("setemc Alice 1000000")
    s.pc("putcell %s 0 Bob true" % N2)
    s.pc("putcell %s 1 Ghost true" % N2)
    time.sleep(1)
    insert(s, N2, COBBLE, 64)  # Bob learns cobblestone
    a_before, b_before = emc(s, "Alice"), emc(s, "Bob")
    check("isolation: Bob's network can't extract Alice-only item", extract(s, N2, IRON, 1) == 0)
    check("isolation: Alice's EMC untouched", emc(s, "Alice") == a_before and emc(s, "Bob") == b_before)
    s.pc("logout Bob")
    time.sleep(1)
    check("two offline owners: Bob's cell lists cobblestone", count(s, N2, COBBLE)[0] == b_before)
    check("never-seen owner: no team created", s.pc("hasteam Ghost").endswith("false"))

    # --- Power Flower owned by offline Alice
    s.pc("flower place %s Alice" % FLOWER)
    return {"alice": emc(s, "Alice"), "bob": emc(s, "Bob")}


def session_b(s, before):
    check("restart: Alice's offline balance persisted", emc(s, "Alice") == before["alice"])
    check("restart: Bob's offline balance persisted", emc(s, "Bob") == before["bob"])
    check("restart: offline-learned knowledge persisted", knows(s, "Alice", IRON))
    v_dia, sell_dia = value(s, DIAMOND)
    check("restart: cell available before owner ever logs in", count(s, N1, DIAMOND)[0] == before["alice"] // v_dia)
    e0 = emc(s, "Alice")
    check("restart: offline insert", insert(s, N1, DIAMOND, 5) == 5 and emc(s, "Alice") == e0 + 5 * sell_dia)
    e1 = emc(s, "Alice")
    check("restart: offline extract", extract(s, N1, DIAMOND, 2) == 2 and emc(s, "Alice") == e1 - 2 * v_dia)
    for _ in range(30):  # wait until the flower has fired at least once with the owner offline
        flower = s.pc("flower info " + FLOWER)
        if num(flower, "stored") > 0:
            break
        time.sleep(1)
    check("flower accumulated backlog while owner offline", num(flower, "stored") > 0, flower)

    # login shortly before the flower fires; the backlog must land on top of the current balance
    s.pc("flower logincheck %s Alice" % FLOWER)
    line = s.wait_for(lambda l: "PCTEST PASS flower-merge" in l or "PCTEST FAIL flower-merge" in l, 400)
    check("flower backlog + offline cell changes merge exactly once", "PASS" in line, line.split("PCTEST ", 1)[1])
    s.raw("setblock %s air" % FLOWER)  # stop income so the balance holds still for the next checks

    # online again: same data, live provider
    e2 = emc(s, "Alice")
    check("after login: offline-learned item still known", knows(s, "Alice", IRON))
    check("after login: count matches live EMC", count(s, N1, DIAMOND)[0] == min(e2 // v_dia, CAP))

    s.pc("respawn Alice")
    e3 = emc(s, "Alice")
    check("after respawn: extract works", extract(s, N1, DIAMOND, 1) == 1 and emc(s, "Alice") == e3 - v_dia)

    # large knowledge set: full knowledge (Tome), owner offline
    s.pc("logout Alice")
    s.pc("fullknowledge Alice true")
    time.sleep(1)
    fresh, _, micros = count(s, N1, COBBLE)
    keys = num(s.pc("count %s %s" % (N1, COBBLE)), "keys")
    check("full knowledge offline: all items listed", fresh > 0 and keys > 100, "keys=%s scan=%sus" % (keys, micros))
    warm = sorted(count(s, N1, COBBLE)[2] for _ in range(5))
    print("INFO full-knowledge warm scan times (us): %s" % warm)
    s.pc("fullknowledge Alice false")
    keys_after = num(s.pc("count %s %s" % (N1, COBBLE)), "keys")
    check("cache: dropping full knowledge shrinks the list at once", keys_after < 100, "keys=%s" % keys_after)
    s.raw("reload", 8)
    e4 = emc(s, "Alice")
    check("cache: counts still right after /reload (EMC remap)", count(s, N1, IRON)[0] == min(e4 // value(s, IRON)[0], CAP))
    s.pc("flower place %s Alice" % FLOWER)
    return {"alice": emc(s, "Alice")}


def session_c(s, before):
    check("restart 2: balance persisted", emc(s, "Alice") == before["alice"])
    time.sleep(25)  # let the flower build a fresh backlog with no cell activity
    s.pc("flower logincheck %s Alice" % FLOWER)
    line = s.wait_for(lambda l: "PCTEST PASS flower-merge" in l or "PCTEST FAIL flower-merge" in l, 400)
    check("flower merge without cell activity", "PASS" in line, line.split("PCTEST ", 1)[1])


SERVERS = []


def main():
    try:
        run()
    finally:
        for s in SERVERS:
            s.kill()


def run():
    server_dir, jar = sys.argv[1], sys.argv[2]
    prepare(server_dir, jar)
    if "--bench" in sys.argv:
        bench(server_dir)
        return
    if "--baseline" in sys.argv:
        baseline(server_dir)
        return
    s = Server(server_dir)
    s.start()
    state = session_a(s)
    s.stop()
    set_flower_delay(server_dir, 100)

    s = Server(server_dir)
    s.start()
    state = session_b(s, state)
    s.stop()

    s = Server(server_dir)
    s.start()
    session_c(s, state)
    s.stop()

    failed = [l for l, ok in results if not ok]
    print("\n%d checks, %d failed" % (len(results), len(failed)))
    for l in failed:
        print("  FAILED: " + l)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
