To access the server, ssh into the host named `plzhackme`.

All the docker services, containers, volumes, and everything should beging with a `hirusha-` prefix.

All the stuff related to the current project should be in it's own network when communicating with stuff. For example, for this project, it should be hirusha-labellens (external: false). But then, the services that should be exposed publicly should be given access to `intranet_1`, which has already been created (external: true).

At the end of the docker compose file, there would be something like this:

```
networks:
  intranet_1:
    external: true
  hirusha-labellens:
    external: false
```

And for services being exposed publicly, they should be a part of both the networks obviously (if needed, but defnitely the public one).

```
networks:
    - intranet_1
    - hirusha-labellens
```

All the volumes should be mounted directly to the disk, like `./`.

All the services being exposed publicly will be inside the `/etc/caddy/Caddyfile` file. Dont touch anything else other than the main project that we are dealing with and dont make any changes that might break any project/domain hosted with caddy or in this server.

To reload the Caddy configuration, simply run the `caddyr` alias/command.

All of the projects should be inside `/srv/`. This project will be in `/srv/hirusha-labellens/`. When you update stuff, git pull and then do whatever it is you are doing.

Caddy is also in the same `intranet_1`. Inside the Caddyfile, this is how you should reverse proxy, assuming the service/container name is `hirusha-labellens-web` and it's internal port is 3000.

```
api.label-lens.cc {
    reverse_proxy hirusha-labellens-web:3000
}
```

