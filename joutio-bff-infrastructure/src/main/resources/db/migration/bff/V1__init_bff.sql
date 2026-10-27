-- Joutio BFF — baseline schema (squashed from V1..V3)
-- Generated from a fresh PostgreSQL 16 database after applying the legacy migrations.

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.13
-- Dumped by pg_dump version 16.13

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

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
    created_at timestamp without time zone NOT NULL
);


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


