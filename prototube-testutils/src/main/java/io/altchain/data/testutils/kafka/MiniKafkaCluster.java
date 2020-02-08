package io.altchain.data.testutils.kafka;

import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import org.apache.commons.io.FileUtils;
import org.apache.kafka.common.network.ListenerName;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.kafka.common.utils.Time;
import scala.Option;
import scala.collection.Seq;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public final class MiniKafkaCluster implements Closeable {
  private final EmbeddedZooKeeper zkServer;
  private final ArrayList<KafkaServer> kafkaServer;
  private final Path tempDir;

  @SuppressWarnings({"rawtypes", "unchecked"})
  private MiniKafkaCluster(List<String> brokerIds) throws IOException, InterruptedException {
    this.zkServer = new EmbeddedZooKeeper();
    this.tempDir = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), "mini-kafka-cluster");
    this.kafkaServer = new ArrayList<>();
    for (String id : brokerIds) {
      KafkaConfig c = new KafkaConfig(createBrokerConfig(id));
      Seq seq = scala.collection.JavaConverters.collectionAsScalaIterableConverter(Collections.emptyList()).asScala()
          .toSeq();
      kafkaServer.add(new KafkaServer(c, Time.SYSTEM, Option.empty(), seq));
    }
  }

  private Properties createBrokerConfig(String nodeId) throws IOException {
    Properties props = new Properties();
    props.put("broker.id", nodeId);
    props.put("port", Integer.toString(KafkaTestUtil.getAvailablePort()));
    props.put("log.dir", Files.createTempDirectory(tempDir, "broker-").toAbsolutePath().toString());
    props.put("zookeeper.connect", "127.0.0.1:" + zkServer.getPort());
    props.put("replica.socket.timeout.ms", "1500");
    props.put("controller.socket.timeout.ms", "1500");
    props.put("controlled.shutdown.enable", "true");
    props.put("delete.topic.enable", "false");
    props.put("controlled.shutdown.retry.backoff.ms", "100");
    props.put("log.cleaner.dedupe.buffer.size", "2097152");
    return props;
  }

  public void start() {
    for (KafkaServer s : kafkaServer) {
      s.startup();
    }
  }

  @Override
  public void close() throws IOException {
    for (KafkaServer s : kafkaServer) {
      s.shutdown();
    }
    this.zkServer.close();
    FileUtils.deleteDirectory(tempDir.toFile());
  }

  public EmbeddedZooKeeper getZkServer() {
    return zkServer;
  }

  public List<KafkaServer> getKafkaServer() {
    return kafkaServer;
  }

  public int getKafkaServerPort(int index) {
    return kafkaServer.get(index).socketServer().boundPort(ListenerName
        .forSecurityProtocol(SecurityProtocol.PLAINTEXT));
  }

  public static class Builder {
    private List<String> brokerIds = new ArrayList<>();

    public Builder newServer(String brokerId) {
      brokerIds.add(brokerId);
      return this;
    }

    public MiniKafkaCluster build() throws IOException, InterruptedException {
      return new MiniKafkaCluster(brokerIds);
    }
  }
}
