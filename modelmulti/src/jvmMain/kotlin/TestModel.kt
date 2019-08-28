actual class Foo actual constructor(val bar: String) {
    actual fun frob() {
        println("JVM Frobbing the $bar")
    }
}
