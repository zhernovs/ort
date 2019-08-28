actual class Foo actual constructor(val bar: String) {
    actual fun frob() {
        console.log("JS Frobbing the $bar")
    }
}

fun fooFrob(bar: String) = console.log("Function frobbing the $bar")
