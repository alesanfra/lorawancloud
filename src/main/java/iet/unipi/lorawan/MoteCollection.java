package iet.unipi.lorawan;


import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MoteCollection implements ConcurrentMap<String,Mote> {
    private final ConcurrentHashMap<String,Mote> motesByAddress;
    private final ConcurrentHashMap<String,String> euiToAddress;

    public MoteCollection() {
        motesByAddress = new ConcurrentHashMap<>();
        euiToAddress = new ConcurrentHashMap<>();
    }

    @Override
    public int size() {
        return motesByAddress.size();
    }

    @Override
    public boolean isEmpty() {
        return motesByAddress.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return motesByAddress.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return motesByAddress.containsValue(value);
    }

    @Override
    public Mote get(Object key) {
        return motesByAddress.get(key);
    }

    @Override
    public Mote put(String key, Mote value) {
        return this.put(value);
    }

    @Override
    public Mote remove(Object key) {
        Mote mote = motesByAddress.remove(key);

        if (mote != null) {
            euiToAddress.remove(mote.getDevEUI());
        }

        return mote;
    }

    @Override
    public void putAll(Map<? extends String, ? extends Mote> m) {

    }

    @Override
    public void clear() {
        this.motesByAddress.clear();
        this.euiToAddress.clear();
    }

    @Override
    public Set<String> keySet() {
        return motesByAddress.keySet();
    }

    @Override
    public Collection<Mote> values() {
        return motesByAddress.values();
    }

    @Override
    public Set<Entry<String, Mote>> entrySet() {
        return motesByAddress.entrySet();
    }

    @Override
    public Mote putIfAbsent(String key, Mote value) {
        return null;
    }

    @Override
    public boolean remove(Object key, Object value) {
        if (this.containsKey(key)) {
            this.remove(key);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean replace(String key, Mote oldValue, Mote newValue) {
        if (motesByAddress.replace(key,oldValue,newValue)) {
            if (!oldValue.getDevEUI().equals(newValue.getDevEUI())) {
                euiToAddress.remove(oldValue.getDevEUI());
                euiToAddress.put(newValue.getDevEUI(),key);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Mote replace(String key, Mote value) {
        Mote old = motesByAddress.replace(key,value);

        if (old != null && !old.getDevEUI().equals(value.getDevEUI())) {
            euiToAddress.remove(old.getDevEUI());
            euiToAddress.put(value.getDevEUI(),key);
        }

        return old;
    }


    /**
     * Extension to support EUI
     */

    public Mote getByEui(String key) {
        return motesByAddress.get(euiToAddress.get(key));
    }

    public boolean containsEui(String key) {
        return euiToAddress.containsKey(key);
    }

    public Mote removeByEui(String key) {
        String address = euiToAddress.get(key);
        euiToAddress.remove(key);
        return motesByAddress.remove(address);
    }

    public Mote put(Mote value) {
        euiToAddress.put(value.getDevEUI(),value.getDevAddress());
        return motesByAddress.put(value.getDevAddress(),value);
    }

}
