
docker-compose exec kafka  kafka-topics --create --topic incoming-request  --partitions 4 --replication-factor 1 --if-not-exists --zookeeper zookeeper:32181
docker-compose exec kafka  kafka-topics --create --topic incoming-request-json  --partitions 4 --replication-factor 1 --if-not-exists --zookeeper zookeeper:32181
docker-compose exec kafka  kafka-topics --create --topic proto-span-start-stop  --partitions 4 --replication-factor 1 --if-not-exists --zookeeper zookeeper:32181
docker-compose exec kafka  kafka-topics --create --topic span-start-stop --partitions 4 --replication-factor 1 --if-not-exists --zookeeper zookeeper:32181
docker-compose exec kafka  kafka-topics --create --topic proto-span-log --partitions 4 --replication-factor 1 --if-not-exists --zookeeper zookeeper:32181
docker-compose exec kafka  kafka-topics --create --topic span-metadata --partitions 4 --replication-factor 1 --if-not-exists --zookeeper zookeeper:32181
docker-compose exec kafka  kafka-topics --create --topic span-log-aggregated --partitions 4 --replication-factor 1 --if-not-exists --zookeeper zookeeper:32181
docker-compose exec kafka  kafka-topics --create --topic fat-trace-object --partitions 4 --replication-factor 1 --if-not-exists --zookeeper zookeeper:32181
docker-compose exec kafka  kafka-topics --create --topic fat-incomplete-trace-object --partitions 4 --replication-factor 1 --if-not-exists --zookeeper zookeeper:32181