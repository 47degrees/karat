package fp.serrano.karat.examples.reflect

data class Queue<Message>(val partitions: Set<Partition<Message>>)
data class Partition<Message>(var messages: List<Message>)