package mindrift.app.lisynchronization.core.cache;

public class CacheEntry {
    private String key;
    private Object data;
    private String provider;
    private long expireAt;

    public CacheEntry() {}

    public CacheEntry(String key, Object data, String provider, long expireAt) {
        this.key = key;
        this.data = data;
        this.provider = provider;
        this.expireAt = expireAt;
    }

    public String getKey() {
        return key;
    }

    public Object getData() {
        return data;
    }

    public String getProvider() {
        return provider;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }
}






