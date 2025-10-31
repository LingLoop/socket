package song.lingloop.netty.repository;

import io.netty.channel.Channel;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RoomRepository {

    private final Map<String, Map<String, Channel>> rooms = new ConcurrentHashMap<>();

    /** 방에 참가자를 등록 */
    public void join(String room, String peerId, Channel channel) {
        rooms.computeIfAbsent(room, r -> new ConcurrentHashMap<>()).put(peerId, channel);
    }

    /** 방에서 특정 참가자 제거, 비면 방 삭제 */
    public void leave(String room, String peerId) {
        Map<String, Channel> peers = rooms.get(room);
        if (peers != null) {
            peers.remove(peerId);
            if (peers.isEmpty()) {
                rooms.remove(room);
            }
        }
    }

    /** 특정 방의 모든 참가자 반환 */
    public Map<String, Channel> getPeers(String room) {
        return rooms.getOrDefault(room, Map.of());
    }

    /** 특정 방에 peerId가 존재하는지 */
    public boolean hasPeer(String room, String peerId) {
        return rooms.containsKey(room) && rooms.get(room).containsKey(peerId);
    }

    /** 특정 참가자의 Channel 가져오기 */
    public Channel getChannel(String room, String peerId) {
        Map<String, Channel> peers = rooms.get(room);
        return peers != null ? peers.get(peerId) : null;
    }

    /** 방 전체 알림용 */
    public void broadcast(String room, String message, String excludePeer) {
        Map<String, Channel> peers = rooms.get(room);
        if (peers == null) return;
        peers.forEach((peer, ch) -> {
            if (!peer.equals(excludePeer)) {
                ch.writeAndFlush(message);
            }
        });
    }

    /** 전체 상태 확인용 (디버깅) */
    public Map<String, Map<String, Channel>> snapshot() {
        return Map.copyOf(rooms);
    }
}
