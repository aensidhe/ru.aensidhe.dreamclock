package ru.aensidhe.dreamclock.immich

interface CredentialsStore {
    fun current(): ImmichCredentials?
}

object NoCredentialsStore : CredentialsStore {
    override fun current(): ImmichCredentials? = null
}

class StaticCredentialsStore(
    private val credentials: ImmichCredentials?,
) : CredentialsStore {
    override fun current(): ImmichCredentials? = credentials
}
