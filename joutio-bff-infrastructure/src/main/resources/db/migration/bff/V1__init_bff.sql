-- Joutio bff — schéma complet (pg_dump, PostgreSQL 16).
-- V1 unique avant l'ouverture publique : toute évolution modifie ce fichier et impose une remise à zéro
-- de la base (docs/backend/conventions.md, section Base de données).

--
-- PostgreSQL database dump
--



SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: content_notice; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_notice (
    id uuid NOT NULL,
    status character varying(16) NOT NULL,
    notifier_name character varying(160) NOT NULL,
    notifier_email character varying(254) NOT NULL,
    notifier_quality character varying(16) NOT NULL,
    content_url character varying(1024) NOT NULL,
    category character varying(32) NOT NULL,
    description character varying(4000) NOT NULL,
    rights_evidence character varying(2000),
    good_faith boolean NOT NULL,
    reporter_user_id character varying(64),
    received_at timestamp without time zone NOT NULL,
    decided_at timestamp without time zone,
    decided_by character varying(64),
    decision character varying(32),
    decision_reason character varying(1000)
);


--
-- Name: TABLE content_notice; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.content_notice IS 'Notifications de contenu illicite (LCEN/DSA) et décisions de la modération';


--
-- Name: expo_push_delivery; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expo_push_delivery (
    id character varying(36) NOT NULL,
    notification_id character varying(64) NOT NULL,
    installation_id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    token character varying(255) NOT NULL,
    type character varying(40) NOT NULL,
    source_id character varying(64),
    game_id character varying(64),
    expires_at timestamp with time zone NOT NULL,
    ticket character varying(255),
    attempts integer DEFAULT 0 NOT NULL,
    next_at timestamp with time zone DEFAULT now() NOT NULL,
    done boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: expo_push_subscription; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expo_push_subscription (
    installation_id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    token character varying(255) NOT NULL,
    platform character varying(10) NOT NULL,
    rematches boolean NOT NULL,
    registered_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: media_asset; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.media_asset (
    id uuid NOT NULL,
    object_key character varying(512) NOT NULL,
    url character varying(1024) NOT NULL,
    content_type character varying(64) NOT NULL,
    size_bytes bigint NOT NULL,
    width integer NOT NULL,
    height integer NOT NULL,
    sha256 character varying(64) NOT NULL,
    uploaded_by character varying(64) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    rights_declared_at timestamp without time zone,
    terms_version character varying(32),
    source_note character varying(255)
);


--
-- Name: COLUMN media_asset.rights_declared_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.media_asset.rights_declared_at IS 'Déclaration de droits de l''auteur (CGU)';


--
-- Name: push_subscription; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.push_subscription (
    endpoint character varying(1024) NOT NULL,
    user_id character varying(64) NOT NULL,
    p256dh character varying(255) NOT NULL,
    auth character varying(255) NOT NULL,
    user_agent character varying(255),
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: content_notice content_notice_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_notice
    ADD CONSTRAINT content_notice_pkey PRIMARY KEY (id);


--
-- Name: expo_push_delivery expo_push_delivery_notification_id_installation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expo_push_delivery
    ADD CONSTRAINT expo_push_delivery_notification_id_installation_id_key UNIQUE (notification_id, installation_id);


--
-- Name: expo_push_delivery expo_push_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expo_push_delivery
    ADD CONSTRAINT expo_push_delivery_pkey PRIMARY KEY (id);


--
-- Name: expo_push_subscription expo_push_subscription_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expo_push_subscription
    ADD CONSTRAINT expo_push_subscription_pkey PRIMARY KEY (installation_id);


--
-- Name: expo_push_subscription expo_push_subscription_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expo_push_subscription
    ADD CONSTRAINT expo_push_subscription_token_key UNIQUE (token);


--
-- Name: media_asset media_asset_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.media_asset
    ADD CONSTRAINT media_asset_pkey PRIMARY KEY (id);


--
-- Name: push_subscription push_subscription_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.push_subscription
    ADD CONSTRAINT push_subscription_pkey PRIMARY KEY (endpoint);


--
-- Name: idx_content_notice_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_content_notice_status ON public.content_notice USING btree (status, received_at DESC);


--
-- Name: idx_expo_push_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expo_push_due ON public.expo_push_delivery USING btree (next_at) WHERE (done = false);


--
-- Name: idx_expo_push_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expo_push_user ON public.expo_push_subscription USING btree (user_id);


--
-- Name: idx_media_asset_object_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_media_asset_object_key ON public.media_asset USING btree (object_key);


--
-- Name: idx_media_asset_sha256; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_media_asset_sha256 ON public.media_asset USING btree (sha256);


--
-- Name: idx_media_asset_uploaded_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_media_asset_uploaded_by ON public.media_asset USING btree (uploaded_by);


--
-- Name: idx_push_subscription_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_push_subscription_user ON public.push_subscription USING btree (user_id);


--
-- PostgreSQL database dump complete
--


